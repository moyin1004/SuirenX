package auth

import (
	"errors"
	"strings"
	"testing"
	"time"

	"golang.org/x/crypto/bcrypt"
)

type memoryRepository struct {
	accounts                map[string]*Account
	registrationEnabled     bool
	registrationSettingSeen bool
}

func (r *memoryRepository) CreateAccount(account *Account) error {
	if r.accounts == nil {
		r.accounts = map[string]*Account{}
	}
	r.accounts[account.Username] = account
	return nil
}

func (r *memoryRepository) CreateRegisteredAccount(account *Account) error {
	if r.registrationSettingSeen && !r.registrationEnabled {
		return ErrRegistrationDisabled
	}
	return r.CreateAccount(account)
}

func (r *memoryRepository) FindAccount(username string) (*Account, error) {
	return r.accounts[username], nil
}

func (r *memoryRepository) FindAccountByID(id string) (*Account, error) {
	for _, account := range r.accounts {
		if account.ID == id {
			return account, nil
		}
	}
	return nil, nil
}

func (r *memoryRepository) UpdateSuperadminPassword(username string, passwordHash []byte, _ time.Time) error {
	account := r.accounts[username]
	if account == nil || account.Role != RoleSuperadmin {
		return ErrSuperadminNotFound
	}
	account.PasswordHash = append([]byte(nil), passwordHash...)
	return nil
}

func TestInvalidRegistrationRejectedBeforeRepositoryAccess(t *testing.T) {
	for _, account := range []struct{ username, password string }{
		{" ", "password"},
		{strings.Repeat("中", 34), "password"},
		{"tester", "short"},
		{"tester", strings.Repeat("a", 73)},
		{"tester", strings.Repeat("中", 25)},
		{"tester", "password中"},
		{"tester", "password😀"},
		{"tester", "password\x00"},
		{"tester", "password\n"},
		{"tester", "password\x7f"},
	} {
		service, err := NewService(nil, []byte(strings.Repeat("s", MinimumKeyBytes)))
		if err != nil {
			t.Fatal(err)
		}
		_, _, err = service.Register(account.username, account.password, "peer-a")
		if !errors.Is(err, ErrInvalidAccount) {
			t.Fatalf("expected invalid account, got %v", err)
		}
	}
}

func TestRegistrationAcceptsPrintableASCIIBoundariesWithoutTrimming(t *testing.T) {
	for _, password := range []string{
		strings.Repeat("a", 8),
		strings.Repeat("1", 72),
		" ~!@#$%^&*()_+-=[]{}|;:',.<>/? ",
	} {
		t.Run(password, func(t *testing.T) {
			repository := &memoryRepository{accounts: map[string]*Account{}}
			service, err := NewService(repository, []byte(strings.Repeat("s", MinimumKeyBytes)))
			if err != nil {
				t.Fatal(err)
			}
			if _, _, err := service.Register("用户", password, "peer-a"); err != nil {
				t.Fatalf("valid ASCII password rejected: %v", err)
			}
			if _, _, err := service.Login("用户", password, "peer-a"); err != nil {
				t.Fatalf("registered password cannot log in: %v", err)
			}
			if password != strings.TrimSpace(password) {
				if _, _, err := service.Login("用户", strings.TrimSpace(password), "peer-a"); !errors.Is(err, ErrInvalidCredentials) {
					t.Fatalf("password spaces must remain significant: %v", err)
				}
			}
		})
	}
}

func TestLoginPreservesLegacyUnicodePasswords(t *testing.T) {
	password := "原有密码123"
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		t.Fatal(err)
	}
	repository := &memoryRepository{accounts: map[string]*Account{
		"existing": {ID: "legacy-account", Username: "existing", PasswordHash: hash, Role: RoleUser},
	}}
	service, err := NewService(repository, []byte(strings.Repeat("s", MinimumKeyBytes)))
	if err != nil {
		t.Fatal(err)
	}
	if _, _, err := service.Login("existing", password, "peer-a"); err != nil {
		t.Fatalf("existing password stopped working: %v", err)
	}
}

func TestRegistrationDisabledIsReturnedByRepository(t *testing.T) {
	repository := &memoryRepository{accounts: map[string]*Account{}, registrationSettingSeen: true}
	service, err := NewService(repository, []byte(strings.Repeat("s", MinimumKeyBytes)))
	if err != nil {
		t.Fatal(err)
	}
	if _, _, err := service.Register("tester", "correct horse battery staple", "peer-a"); !errors.Is(err, ErrRegistrationDisabled) {
		t.Fatalf("expected registration disabled, got %v", err)
	}
	if _, ok := repository.accounts["tester"]; ok {
		t.Fatal("disabled registration created an account")
	}
}

func TestSuperadminBootstrapDoesNotResetPasswordAndRejectsCollision(t *testing.T) {
	repository := &memoryRepository{accounts: map[string]*Account{}}
	service, err := NewService(repository, []byte(strings.Repeat("s", MinimumKeyBytes)))
	if err != nil {
		t.Fatal(err)
	}
	if err := service.BootstrapSuperadmin("operator", "correct first secret"); err != nil {
		t.Fatal(err)
	}
	account, _ := repository.FindAccount("operator")
	originalHash := append([]byte(nil), account.PasswordHash...)
	if err := service.BootstrapSuperadmin("operator", "different second secret"); err != nil {
		t.Fatal(err)
	}
	if string(account.PasswordHash) != string(originalHash) {
		t.Fatal("ordinary bootstrap changed the administrator password")
	}
	if _, _, err := service.Login("operator", "correct first secret", "peer-a"); err != nil {
		t.Fatalf("original configured password no longer works: %v", err)
	}
	if _, _, err := service.Login("operator", "different second secret", "peer-a"); !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("bootstrap unexpectedly adopted a rotated password: %v", err)
	}
	if account.Role != RoleSuperadmin {
		t.Fatalf("role = %q", account.Role)
	}
	if err := service.BootstrapSuperadmin("operator", "short"); !errors.Is(err, ErrAdminBootstrap) {
		t.Fatalf("invalid configured password should fail: %v", err)
	}

	if _, _, err := service.Register("member", "correct member password", "peer-a"); err != nil {
		t.Fatal(err)
	}
	if err := service.BootstrapSuperadmin("member", "new admin secret long"); !errors.Is(err, ErrAdminNameCollision) {
		t.Fatalf("existing user collision should fail: %v", err)
	}
}

func TestJWTIsSignedAndParseOnlyDoesNotReplaceVerification(t *testing.T) {
	secret := []byte(strings.Repeat("s", MinimumKeyBytes))
	repository := &memoryRepository{accounts: map[string]*Account{}}
	service, err := NewService(repository, secret)
	if err != nil {
		t.Fatal(err)
	}
	token, expiresAt, err := service.Register("tester", "correct horse battery staple", "peer-a")
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
