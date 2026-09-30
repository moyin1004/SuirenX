package auth

import (
	"errors"
	"strings"
	"testing"
)

type memoryRepository struct {
	accounts map[string]*Account
}

func (r *memoryRepository) CreateAccount(account *Account) error {
	if r.accounts == nil {
		r.accounts = map[string]*Account{}
	}
	r.accounts[account.Username] = account
	return nil
}

func (r *memoryRepository) FindAccount(username string) (*Account, error) {
	return r.accounts[username], nil
}

func TestInvalidRegistrationRejectedBeforeRepositoryAccess(t *testing.T) {
	for _, account := range []struct{ username, password string }{
		{" ", "password"},
		{strings.Repeat("中", 34), "password"},
		{"tester", "short"},
		{"tester", strings.Repeat("a", 73)},
		{"tester", strings.Repeat("中", 25)},
	} {
		service, err := NewService(nil, []byte(strings.Repeat("s", MinimumKeyBytes)))
		if err != nil {
			t.Fatal(err)
		}
		_, _, err = service.Register(account.username, account.password)
		if !errors.Is(err, ErrInvalidAccount) {
			t.Fatalf("expected invalid account, got %v", err)
		}
	}
}

func TestJWTIsSignedAndParseOnlyDoesNotReplaceVerification(t *testing.T) {
	secret := []byte(strings.Repeat("s", MinimumKeyBytes))
	repository := &memoryRepository{accounts: map[string]*Account{}}
	service, err := NewService(repository, secret)
	if err != nil {
		t.Fatal(err)
	}
	token, expiresAt, err := service.Register("tester", "correct horse battery staple")
	if err != nil {
		t.Fatal(err)
	}
	if !expiresAt.After(service.now()) || strings.Count(token, ".") != 2 {
		t.Fatalf("expected standard jwt with future expiry, got %q", token)
	}
	claims, err := service.Authenticate(token)
	if err != nil || claims.Subject == "" || claims.Username != "tester" {
		t.Fatalf("authenticate jwt: claims=%+v err=%v", claims, err)
	}

	other, err := NewService(repository, []byte(strings.Repeat("x", MinimumKeyBytes)))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := other.Authenticate(token); !errors.Is(err, ErrInvalidToken) {
		t.Fatalf("wrong signing key should fail verification, got %v", err)
	}
	parsed, err := other.Parse(token)
	if err != nil || parsed.Subject != claims.Subject {
		t.Fatalf("parse-only claims: %+v %v", parsed, err)
	}
}
