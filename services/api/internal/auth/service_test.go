package auth

import (
	"errors"
	"strings"
	"testing"
)

func TestInvalidRegistrationRejectedBeforeRepositoryAccess(t *testing.T) {
	for _, account := range []struct{ username, password string }{
		{" ", "password"},
		{strings.Repeat("中", 34), "password"},
		{"tester", "short"},
		{"tester", strings.Repeat("a", 73)},
		{"tester", strings.Repeat("中", 25)},
	} {
		_, err := NewService(nil).Register(account.username, account.password)
		if !errors.Is(err, ErrInvalidAccount) {
			t.Fatalf("expected invalid account, got %v", err)
		}
	}
}
