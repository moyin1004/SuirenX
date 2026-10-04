package auth

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

const (
	TokenTTL        = 30 * 24 * time.Hour
	MinimumKeyBytes = 32
	issuer          = "suirenx"
)

var (
	ErrInvalidCredentials   = errors.New("invalid credentials")
	ErrUsernameTaken        = errors.New("username is already registered")
	ErrInvalidAccount       = errors.New("username and password are required")
	ErrInvalidToken         = errors.New("invalid or expired bearer token")
	ErrInvalidJWTConfig     = errors.New("jwt secret must be at least 32 bytes")
	ErrAccountDisabled      = errors.New("account is disabled")
	ErrAdminBootstrap       = errors.New("invalid superadmin bootstrap configuration")
	ErrAdminNameCollision   = errors.New("configured superadmin username belongs to a non-admin account")
	ErrSuperadminRequired   = errors.New("superadmin account required")
	ErrSuperadminNotFound   = errors.New("configured superadmin account not found")
	ErrRegistrationDisabled = errors.New("account registration is disabled")
)

const (
	RoleUser       = "USER"
	RoleSuperadmin = "SUPERADMIN"
)

type Account struct {
	ID           string
	Username     string
	PasswordHash []byte
	CreatedAt    time.Time
	Role         string
	DisabledAt   *time.Time
}

// Claims is the application data carried by a signed JWT. The middleware is
// the only place where these claims are trusted for authorization.
type Claims struct {
	Username string `json:"username"`
	Role     string `json:"role"`
	jwt.RegisteredClaims
}

type Repository interface {
	CreateAccount(account *Account) error
	CreateRegisteredAccount(account *Account) error
	FindAccount(username string) (*Account, error)
	FindAccountByID(id string) (*Account, error)
	UpdateSuperadminPassword(username string, passwordHash []byte, now time.Time) error
}

type Service struct {
	repository Repository
	secret     []byte
	now        func() time.Time
	tokenTTL   time.Duration
}

func NewService(repository Repository, secret []byte) (*Service, error) {
	if len(secret) < MinimumKeyBytes {
		return nil, ErrInvalidJWTConfig
	}
	return &Service{
		repository: repository,
		secret:     append([]byte(nil), secret...),
		now:        time.Now,
		tokenTTL:   TokenTTL,
	}, nil
}

func (s *Service) Register(username, password string) (string, time.Time, error) {
	username = strings.TrimSpace(username)
	if username == "" || len(password) < 8 || len(password) > 72 || len(username) > 100 {
		return "", time.Time{}, ErrInvalidAccount
	}
	existing, err := s.repository.FindAccount(username)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("find account: %w", err)
	}
	if existing != nil {
		return "", time.Time{}, ErrUsernameTaken
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("hash password: %w", err)
	}
	now := s.now().UTC()
	account := &Account{ID: newID(), Username: username, PasswordHash: hash, CreatedAt: now, Role: RoleUser}
	if err := s.repository.CreateRegisteredAccount(account); err != nil {
		return "", time.Time{}, fmt.Errorf("create account: %w", err)
	}
	return s.issue(account)
}

func (s *Service) Login(username, password string) (string, time.Time, error) {
	account, err := s.repository.FindAccount(strings.TrimSpace(username))
	if err != nil || account == nil || bcrypt.CompareHashAndPassword(account.PasswordHash, []byte(password)) != nil {
		return "", time.Time{}, ErrInvalidCredentials
	}
	if account.DisabledAt != nil {
		return "", time.Time{}, ErrAccountDisabled
	}
	return s.issue(account)
}

// BootstrapSuperadmin creates the configured initial administrator once. It
// never changes an existing password and fails closed on a username collision.
func (s *Service) BootstrapSuperadmin(username, password string) error {
	username = strings.TrimSpace(username)
	if username == "" || len(username) > 100 || len(password) < 12 || len(password) > 72 {
		return ErrAdminBootstrap
	}
	account, err := s.repository.FindAccount(username)
	if err != nil {
		return fmt.Errorf("find configured superadmin: %w", err)
	}
	if account != nil {
		if account.Role != RoleSuperadmin {
			return ErrAdminNameCollision
		}
		return nil
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return fmt.Errorf("hash configured superadmin password: %w", err)
	}
	account = &Account{ID: newID(), Username: username, PasswordHash: hash, CreatedAt: s.now().UTC(), Role: RoleSuperadmin}
	if err := s.repository.CreateAccount(account); err != nil {
		return fmt.Errorf("create configured superadmin: %w", err)
	}
	return nil
}

// RotateSuperadminPassword is an explicit operator action; BootstrapSuperadmin
// intentionally never calls it during ordinary service startup.
func (s *Service) RotateSuperadminPassword(username, password string) error {
	username = strings.TrimSpace(username)
	if username == "" || len(password) < 12 || len(password) > 72 {
		return ErrAdminBootstrap
	}
	account, err := s.repository.FindAccount(username)
	if err != nil {
		return fmt.Errorf("find superadmin for password rotation: %w", err)
	}
	if account == nil || account.Role != RoleSuperadmin {
		return ErrSuperadminNotFound
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return fmt.Errorf("hash rotated superadmin password: %w", err)
	}
	if err := s.repository.UpdateSuperadminPassword(username, hash, s.now().UTC()); err != nil {
		return fmt.Errorf("rotate superadmin password: %w", err)
	}
	return nil
}

// Authenticate verifies the JWT signature, algorithm, issuer and registered
// claims before returning application claims for authorization.
func (s *Service) Authenticate(value string) (Claims, error) {
	if strings.TrimSpace(value) == "" {
		return Claims{}, ErrInvalidToken
	}
	var claims Claims
	token, err := jwt.ParseWithClaims(value, &claims, func(token *jwt.Token) (any, error) {
		if token.Method != jwt.SigningMethodHS256 {
			return nil, ErrInvalidToken
		}
		return s.secret, nil
	}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}), jwt.WithIssuer(issuer), jwt.WithAudience("suirenx-api"))
	if err != nil || token == nil || !token.Valid || claims.Subject == "" {
		return Claims{}, ErrInvalidToken
	}
	return claims, nil
}

// Parse decodes JWT claims without checking the signature. It is deliberately
// separate from Authenticate and must never be used for identity or access
// control decisions.
func (s *Service) Parse(value string) (Claims, error) {
	if strings.TrimSpace(value) == "" {
		return Claims{}, ErrInvalidToken
	}
	var claims Claims
	if _, _, err := jwt.NewParser().ParseUnverified(value, &claims); err != nil || claims.Subject == "" {
		return Claims{}, ErrInvalidToken
	}
	return claims, nil
}

func (s *Service) issue(account *Account) (string, time.Time, error) {
	now := s.now().UTC()
	expires := now.Add(s.tokenTTL)
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, Claims{
		Username: account.Username,
		Role:     account.Role,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    issuer,
			Subject:   account.ID,
			Audience:  []string{"suirenx-api"},
			ExpiresAt: jwt.NewNumericDate(expires),
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
			ID:        newID(),
		},
	})
	value, err := token.SignedString(s.secret)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("sign jwt: %w", err)
	}
	return value, expires, nil
}

func newID() string {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		panic(fmt.Sprintf("generate account id: %v", err))
	}
	return hex.EncodeToString(raw[:])
}
