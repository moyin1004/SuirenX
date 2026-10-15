package auth

import (
	"errors"
	"strconv"
	"strings"
	"testing"
	"time"

	"golang.org/x/crypto/bcrypt"
)

// newTestService builds a service over the in-memory repository with a clock the
// test can move forward.
func newTestService(t *testing.T) (*Service, *memoryRepository, *time.Time) {
	t.Helper()
	repository := &memoryRepository{accounts: map[string]*Account{}}
	service, err := NewService(repository, []byte(strings.Repeat("s", MinimumKeyBytes)))
	if err != nil {
		t.Fatal(err)
	}
	clock := time.Now()
	service.now = func() time.Time { return clock }
	return service, repository, &clock
}

func TestLoginAttemptBudgetBlocksGuessingAndRecoversAfterWindow(t *testing.T) {
	service, _, clock := newTestService(t)
	if _, _, err := service.Register("member", "correct member password", "peer-a"); err != nil {
		t.Fatal(err)
	}
	for attempt := 1; attempt <= loginAccountLimit; attempt++ {
		if _, _, err := service.Login("member", "wrong member password", "peer-a"); !errors.Is(err, ErrInvalidCredentials) {
			t.Fatalf("attempt %d: expected invalid credentials, got %v", attempt, err)
		}
	}
	if _, _, err := service.Login("member", "wrong member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("exhausted account budget still accepted a guess: %v", err)
	}
	// The budget is fail-closed: while it is exhausted the correct password is
	// refused too, so the response never confirms a guess.
	if _, _, err := service.Login("member", "correct member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("exhausted account budget leaked that the password was correct: %v", err)
	}
	*clock = clock.Add(loginAccountWindow)
	token, expiresAt, err := service.Login("member", "correct member password", "peer-a")
	if err != nil || token == "" || !expiresAt.After(*clock) {
		t.Fatalf("budget did not recover after its window: %v", err)
	}
}

func TestUnknownAndDifferentlyCasedAccountsShareTheAccountBudget(t *testing.T) {
	service, _, _ := newTestService(t)
	for attempt := 1; attempt <= loginAccountLimit; attempt++ {
		if _, _, err := service.Login("ghost", "wrong member password", "peer-a"); !errors.Is(err, ErrInvalidCredentials) {
			t.Fatalf("attempt %d: expected invalid credentials, got %v", attempt, err)
		}
	}
	if _, _, err := service.Login("ghost", "wrong member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("unknown accounts are not budgeted: %v", err)
	}
	// Folding case keeps one budget for one account name instead of one per
	// spelling.
	if _, _, err := service.Login("Ghost", "wrong member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("case variation reset the account budget: %v", err)
	}
}

// TestUnknownAccountStillComparesOnePasswordHash guards the timing mitigation:
// if the placeholder hash ever becomes unusable, unknown-account logins would
// skip bcrypt and become distinguishable by response time again.
func TestUnknownAccountStillComparesOnePasswordHash(t *testing.T) {
	hash := credentialPlaceholder()
	if hash == nil {
		t.Fatal("credential placeholder is unavailable, so unknown accounts skip the bcrypt comparison")
	}
	if err := bcrypt.CompareHashAndPassword(hash, []byte("correct member password")); err == nil {
		t.Fatal("credential placeholder matched a password")
	}
}

// TestUnknownAccountLoginPerformsABcryptComparison is the behavioural half of
// the check above: it fails if the comparison is ever short-circuited away. The
// bound is one-sided (at least, never at most), so a slow or loaded machine
// cannot make it fail — only skipping bcrypt can.
func TestUnknownAccountLoginPerformsABcryptComparison(t *testing.T) {
	service, _, _ := newTestService(t)
	credentialPlaceholder() // warm the one-time hash generation outside the measurement
	started := time.Now()
	if _, _, err := service.Login("ghost", "wrong member password", "peer-a"); !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("expected invalid credentials, got %v", err)
	}
	if elapsed := time.Since(started); elapsed < 10*time.Millisecond {
		t.Fatalf("unknown-account login returned in %v, so no bcrypt comparison ran", elapsed)
	}
}

func TestRegistrationBudgetIsPerClaimedUsername(t *testing.T) {
	service, _, _ := newTestService(t)
	if _, _, err := service.Register("member", "correct member password", "peer-a"); err != nil {
		t.Fatal(err)
	}
	for attempt := 2; attempt <= registerAccountLimit; attempt++ {
		if _, _, err := service.Register("member", "correct member password", "peer-a"); !errors.Is(err, ErrUsernameTaken) {
			t.Fatalf("attempt %d: expected username taken, got %v", attempt, err)
		}
	}
	if _, _, err := service.Register("member", "correct member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("repeated probing of one username was not budgeted: %v", err)
	}
}

func TestRegistrationPeerBudgetBoundsAccountCreation(t *testing.T) {
	service, _, _ := newTestService(t)
	for attempt := 1; attempt <= registerPeerLimit; attempt++ {
		if _, _, err := service.Register("user-"+strconv.Itoa(attempt), "correct member password", "peer-a"); err != nil {
			t.Fatalf("registration %d: %v", attempt, err)
		}
	}
	// A name that was never claimed is still refused for the exhausted peer.
	if _, _, err := service.Register("user-fresh", "correct member password", "peer-a"); !errors.Is(err, ErrTooManyAttempts) {
		t.Fatalf("peer budget did not bound account creation: %v", err)
	}
	if _, _, err := service.Register("user-fresh", "correct member password", "peer-b"); err != nil {
		t.Fatalf("another peer inherited the exhausted budget: %v", err)
	}
}

func TestOversizedInvalidAccountsStillConsumeRegistrationPeerBudget(t *testing.T) {
	service, _, _ := newTestService(t)
	for attempt := 0; attempt < registerPeerLimit+2; attempt++ {
		username := strings.Repeat("x", 1<<20) + strconv.Itoa(attempt)
		_, _, err := service.Register(username, "valid password", "same-peer")
		want := ErrInvalidAccount
		if attempt >= registerPeerLimit {
			want = ErrTooManyAttempts
		}
		if !errors.Is(err, want) {
			t.Fatalf("attempt %d: got %v, want %v", attempt, err, want)
		}
	}
}
