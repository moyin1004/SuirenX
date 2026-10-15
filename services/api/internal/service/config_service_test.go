package service

import (
	"errors"
	"strconv"
	"testing"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

// countingConfigRepository implements only the lookup ReadContent needs. The
// embedded interface panics if the service calls anything else, which keeps this
// stub honest about what the limiter is supposed to short-circuit.
type countingConfigRepository struct {
	repository.ConfigRepository
	lookups int
}

func (r *countingConfigRepository) ReadConfigContent(key, tokenHash, suppliedMode string, now time.Time) (string, error) {
	r.lookups++
	return "", repository.ErrApiTokenInvalid
}

func newConfigServiceForTest(t *testing.T) (*ConfigService, *countingConfigRepository, *time.Time) {
	t.Helper()
	stub := &countingConfigRepository{}
	service := NewConfigService(stub)
	clock := time.Now()
	service.now = func() time.Time { return clock }
	return service, stub, &clock
}

// TestReadContentChargesRejectedAttemptsBeforeTheLookup covers the rule that
// failures count: a rejected token is charged to the budget, and once the budget
// is gone the attempt does not reach the store at all.
func TestReadContentChargesRejectedAttemptsBeforeTheLookup(t *testing.T) {
	service, stub, _ := newConfigServiceForTest(t)
	for attempt := 1; attempt <= configReadLimit; attempt++ {
		if _, err := service.ReadContent("cfg", "guessed-token", "", "peer-a"); !errors.Is(err, repository.ErrApiTokenInvalid) {
			t.Fatalf("attempt %d: expected an invalid token error, got %v", attempt, err)
		}
	}
	if stub.lookups != configReadLimit {
		t.Fatalf("lookups = %d, want %d rejected attempts to reach the store", stub.lookups, configReadLimit)
	}
	if _, err := service.ReadContent("cfg", "guessed-token", "", "peer-a"); !errors.Is(err, ErrConfigRateLimited) {
		t.Fatalf("exhausted token budget still reached the store: %v", err)
	}
	if stub.lookups != configReadLimit {
		t.Fatalf("lookups = %d, want the refused attempt to skip the store", stub.lookups)
	}
}

// TestReadContentPeerBudgetBoundsTokenGuessing covers why the token hash cannot
// be the only key: the caller picks that key, so rotating token values would
// otherwise hand out a fresh budget for every guess.
func TestReadContentPeerBudgetBoundsTokenGuessing(t *testing.T) {
	service, _, _ := newConfigServiceForTest(t)
	for attempt := 1; attempt <= configReadPeerLimit; attempt++ {
		if _, err := service.ReadContent("cfg", "guessed-token-"+strconv.Itoa(attempt), "", "peer-a"); err == nil || errors.Is(err, ErrConfigRateLimited) {
			t.Fatalf("attempt %d: expected an invalid token error, got %v", attempt, err)
		}
	}
	if _, err := service.ReadContent("cfg", "guessed-token-fresh", "", "peer-a"); !errors.Is(err, ErrConfigRateLimited) {
		t.Fatalf("rotating token values escaped the peer budget: %v", err)
	}
	if _, err := service.ReadContent("cfg", "guessed-token-fresh", "", "peer-b"); errors.Is(err, ErrConfigRateLimited) {
		t.Fatalf("another peer inherited the exhausted budget: %v", err)
	}
}

// TestReadContentIgnoresMalformedRequestsWithoutAToken documents that the
// budget only charges callers that present exactly one token; a request with no
// token carries no secret to guess and is refused by input validation.
func TestReadContentIgnoresMalformedRequestsWithoutAToken(t *testing.T) {
	service, stub, _ := newConfigServiceForTest(t)
	for attempt := 0; attempt < configReadPeerLimit*2; attempt++ {
		if _, err := service.ReadContent("cfg", "", "", "peer-a"); !errors.Is(err, repository.ErrApiTokenInvalid) {
			t.Fatalf("attempt %d: expected an invalid token error, got %v", attempt, err)
		}
	}
	if stub.lookups != 0 {
		t.Fatalf("malformed requests reached the store %d times", stub.lookups)
	}
	// A well-formed attempt still gets its full budget afterwards.
	if _, err := service.ReadContent("cfg", "guessed-token", "", "peer-a"); !errors.Is(err, repository.ErrApiTokenInvalid) {
		t.Fatalf("malformed flood consumed the budget: %v", err)
	}
}
