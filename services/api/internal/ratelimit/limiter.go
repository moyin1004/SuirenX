// Package ratelimit holds the shared in-process request budgets used by the
// authentication and configuration-read endpoints.
package ratelimit

import (
	"crypto/sha256"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// maxKeys bounds the number of tracked keys so caller-supplied keys (usernames,
// peer addresses, token hashes) cannot grow memory without bound.
const maxKeys = 4096

// Limiter is a keyed token-bucket limiter. Every key gets its own bucket sized
// from the limit and window it is first seen with: the window is the whole
// budget, and refilling that budget takes exactly one window.
type Limiter struct {
	mu      sync.Mutex
	now     func() time.Time
	buckets map[[sha256.Size]byte]*bucket
}

type bucket struct {
	limiter *rate.Limiter
	refill  time.Duration
	seen    time.Time
}

// New returns a limiter driven by now, which callers pass so the limiter shares
// the same clock as the service that owns it (and so tests can advance it).
// now must never move backwards.
func New(now func() time.Time) *Limiter {
	if now == nil {
		now = time.Now
	}
	return &Limiter{now: now, buckets: make(map[[sha256.Size]byte]*bucket)}
}

// Allow consumes one token for key and reports whether it stayed within limit
// events per window. Unlike a fixed window it cannot hand out two full budgets
// around a boundary: after a burst the caller must wait for tokens to refill.
// A key idle for a whole window is full again and is dropped, so unused keys
// hold no memory; when the key cap is reached the least recently seen key is
// evicted, which can only relax the budget under extreme key churn.
func (l *Limiter) Allow(key string, limit int, window time.Duration) bool {
	if limit <= 0 || window <= 0 {
		return true
	}
	// Never retain caller-controlled strings: even a rejected oversized account
	// name reaches this layer before validation and may outlive its request.
	digest := sha256.Sum256([]byte(key))
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()
	for candidate, entry := range l.buckets {
		if now.Sub(entry.seen) >= entry.refill {
			// Refilled to its burst, so the bucket carries no state.
			delete(l.buckets, candidate)
		}
	}
	entry, tracked := l.buckets[digest]
	if !tracked {
		if len(l.buckets) >= maxKeys {
			l.evictLeastRecentlySeen()
		}
		entry = &bucket{
			limiter: rate.NewLimiter(rate.Limit(float64(limit)/window.Seconds()), limit),
			refill:  window,
		}
		l.buckets[digest] = entry
	}
	entry.seen = now
	return entry.limiter.AllowN(now, 1)
}

// evictLeastRecentlySeen drops the key that was used longest ago. Callers hold
// l.mu.
func (l *Limiter) evictLeastRecentlySeen() {
	var oldestKey [sha256.Size]byte
	var oldestSeen time.Time
	found := false
	for key, entry := range l.buckets {
		if !found || entry.seen.Before(oldestSeen) {
			oldestKey, oldestSeen = key, entry.seen
			found = true
		}
	}
	delete(l.buckets, oldestKey)
}
