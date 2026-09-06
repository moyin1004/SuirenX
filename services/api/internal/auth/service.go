package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
)

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrUsernameTaken      = errors.New("username is already registered")
	ErrInvalidAccount     = errors.New("username and password are required")
	ErrInvalidToken       = errors.New("invalid or expired bearer token")
)

type Account struct {
	ID           string
	Username     string
	PasswordHash []byte
	CreatedAt    time.Time
}

type Token struct {
	Value     string
	OwnerID   string
	ExpiresAt time.Time
}

type Repository interface {
	CreateAccount(account *Account) error
	FindAccount(username string) (*Account, error)
	SaveToken(tokenHash, ownerID string, createdAt, expiresAt time.Time) error
	FindToken(tokenHash string, now time.Time) (string, error)
	RevokeTokens(ownerID string) error
}

type Service struct {
	repository Repository
	now        func() time.Time
	tokenTTL   time.Duration
}

func NewService(repository Repository) *Service {
	return &Service{repository: repository, now: time.Now, tokenTTL: 30 * 24 * time.Hour}
}

func (s *Service) Register(username, password string) (Token, error) {
	username = strings.TrimSpace(username)
	if username == "" || len(password) < 8 || len(username) > 100 {
		return Token{}, ErrInvalidAccount
	}
	existing, err := s.repository.FindAccount(username)
	if err != nil {
		return Token{}, fmt.Errorf("find account: %w", err)
	}
	if existing != nil {
		return Token{}, ErrUsernameTaken
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return Token{}, fmt.Errorf("hash password: %w", err)
	}
	account := &Account{ID: newID(), Username: username, PasswordHash: hash, CreatedAt: s.now().UTC()}
	if err := s.repository.CreateAccount(account); err != nil {
		return Token{}, fmt.Errorf("create account: %w", err)
	}
	return s.issue(account.ID)
}

func (s *Service) Login(username, password string) (Token, error) {
	account, err := s.repository.FindAccount(strings.TrimSpace(username))
	if err != nil || account == nil || bcrypt.CompareHashAndPassword(account.PasswordHash, []byte(password)) != nil {
		return Token{}, ErrInvalidCredentials
	}
	return s.issue(account.ID)
}

func (s *Service) Authenticate(value string) (string, error) {
	if value == "" {
		return "", ErrInvalidToken
	}
	hash := hashToken(value)
	ownerID, err := s.repository.FindToken(hash, s.now().UTC())
	if err != nil || ownerID == "" {
		return "", ErrInvalidToken
	}
	return ownerID, nil
}

func (s *Service) Logout(ownerID string) error {
	if ownerID == "" {
		return ErrInvalidToken
	}
	return s.repository.RevokeTokens(ownerID)
}

func (s *Service) issue(ownerID string) (Token, error) {
	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return Token{}, fmt.Errorf("generate token: %w", err)
	}
	now := s.now().UTC()
	expires := now.Add(s.tokenTTL)
	value := hex.EncodeToString(raw[:])
	if err := s.repository.SaveToken(hashToken(value), ownerID, now, expires); err != nil {
		return Token{}, fmt.Errorf("save token: %w", err)
	}
	return Token{Value: value, OwnerID: ownerID, ExpiresAt: expires}, nil
}

func hashToken(value string) string {
	hash := sha256.Sum256([]byte(value))
	return hex.EncodeToString(hash[:])
}

func newID() string {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		panic(fmt.Sprintf("generate account id: %v", err))
	}
	return hex.EncodeToString(raw[:])
}
