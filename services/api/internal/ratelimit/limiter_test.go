package ratelimit

import (
	"crypto/sha256"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestAllowConsumesTheBudgetAndRefillsSmoothly(t *testing.T) {
	clock := time.Now()
	limiter := New(func() time.Time { return clock })
	for attempt := 1; attempt <= 20; attempt++ {
		if !limiter.Allow("account", 20, 5*time.Minute) {
			t.Fatalf("attempt %d should be allowed", attempt)
		}
	}
	if limiter.Allow("account", 20, 5*time.Minute) {
		t.Fatal("attempt 21 should be refused")
	}
	// Tokens refill continuously: 5 minutes / 20 events = one token per 15s.
	clock = clock.Add(15 * time.Second)
	if !limiter.Allow("account", 20, 5*time.Minute) {
		t.Fatal("one refilled token was not usable")
	}
	if limiter.Allow("account", 20, 5*time.Minute) {
		t.Fatal("more than one token refilled in 15s")
	}
	clock = clock.Add(5 * time.Minute)
	for attempt := 1; attempt <= 20; attempt++ {
		if !limiter.Allow("account", 20, 5*time.Minute) {
			t.Fatalf("attempt %d after a full window should be allowed", attempt)
		}
	}
}

// TestAllowCannotDoubleAtWindowBoundary covers the flaw of the fixed window this
// limiter replaced: it handed out a second full budget immediately after the
// boundary, so a caller could spend the whole budget at the tail of one window
// and the whole budget again in the first instant of the next. A token bucket
// only hands out what has actually refilled.
func TestAllowCannotDoubleAtWindowBoundary(t *testing.T) {
	const (
		limit  = 20
		window = 5 * time.Minute
	)
	clock := time.Now()
	limiter := New(func() time.Time { return clock })
	if !limiter.Allow("account", limit, window) {
		t.Fatal("first attempt should be allowed")
	}
	// Spend the rest of the budget at the tail of the window. The bucket has
	// refilled to its burst by now, so this consumes exactly one full budget.
	clock = clock.Add(window - time.Second)
	spent := 0
	for limiter.Allow("account", limit, window) {
		spent++
		if spent > limit {
			t.Fatalf("spent %d attempts at the tail of one window", spent)
		}
	}
	if spent != limit {
		t.Fatalf("spent %d attempts, want the full budget %d", spent, limit)
	}
	// Cross the boundary. A fixed window resets its counter here and hands out
	// the whole budget again; only one refill interval's worth is available.
	clock = clock.Add(2 * time.Second)
	refilled := 0
	for limiter.Allow("account", limit, window) {
		refilled++
	}
	if refilled > 1 {
		t.Fatalf("boundary handed out %d attempts after 2s, i.e. a fresh budget", refilled)
	}
	// One refill interval later exactly one more attempt is available.
	clock = clock.Add(window / limit)
	if !limiter.Allow("account", limit, window) {
		t.Fatal("a refilled token was not usable")
	}
}

func TestAllowKeepsSeparateBucketsPerKey(t *testing.T) {
	clock := time.Now()
	limiter := New(func() time.Time { return clock })
	if !limiter.Allow("first", 2, time.Minute) || !limiter.Allow("first", 2, time.Minute) {
		t.Fatal("first key should get its own budget")
	}
	if limiter.Allow("first", 2, time.Minute) {
		t.Fatal("first key exceeded its budget")
	}
	if !limiter.Allow("second", 2, time.Minute) {
		t.Fatal("second key shared the first key's budget")
	}
}

func TestAllowDropsKeysIdleForAWholeWindow(t *testing.T) {
	clock := time.Now()
	limiter := New(func() time.Time { return clock })
	limiter.Allow("stale", 3, time.Minute)
	clock = clock.Add(time.Minute)
	limiter.Allow("fresh", 3, time.Minute) // every call prunes idle keys
	limiter.mu.Lock()
	_, tracked := limiter.buckets[sha256.Sum256([]byte("stale"))]
	limiter.mu.Unlock()
	if tracked {
		t.Fatal("idle key was retained instead of pruned")
	}
}

func TestAllowBoundsTrackedKeys(t *testing.T) {
	clock := time.Now()
	limiter := New(func() time.Time { return clock })
	for index := 0; index < maxKeys+64; index++ {
		if !limiter.Allow("key-"+strconv.Itoa(index), 5, time.Hour) {
			t.Fatalf("fresh key %d was refused", index)
		}
	}
	limiter.mu.Lock()
	tracked := len(limiter.buckets)
	limiter.mu.Unlock()
	if tracked != maxKeys {
		t.Fatalf("tracked buckets = %d, want exactly the cap %d", tracked, maxKeys)
	}
}

func TestAllowBoundsRetainedBytesForOversizedKeys(t *testing.T) {
	limiter := New(nil)
	for index := 0; index < 12; index++ {
		key := strings.Repeat("x", 1<<20) + strconv.Itoa(index)
		if !limiter.Allow(key, 1, time.Hour) || limiter.Allow(key, 1, time.Hour) {
			t.Fatal("oversized keys must still have separate, enforced budgets")
		}
	}
	retainedBytes := 0
	for key := range limiter.buckets {
		retainedBytes += len(key)
	}
	if retainedBytes > len(limiter.buckets)*128 {
		t.Fatalf("oversized requests retained %d key bytes across %d buckets", retainedBytes, len(limiter.buckets))
	}
}

func TestAllowIgnoresEmptyBudgets(t *testing.T) {
	limiter := New(nil)
	for _, budget := range []struct {
		limit  int
		window time.Duration
	}{{0, time.Minute}, {-1, time.Minute}, {1, 0}, {1, -time.Second}} {
		if !limiter.Allow("key", budget.limit, budget.window) {
			t.Fatalf("budget %d/%v should not limit", budget.limit, budget.window)
		}
	}
}
