package service

import (
	"errors"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

// auditStubRepository implements only the audit read. The embedded interface
// panics if the service starts calling other methods, keeping the stub honest
// about what ListAuditLog is allowed to touch.
type auditStubRepository struct {
	repository.AdminRepository
	entries []domain.AdminAuditEntry
	err     error
	limit   int
}

func (r *auditStubRepository) ListAuditLog(limit int) ([]domain.AdminAuditEntry, error) {
	r.limit = limit
	return r.entries, r.err
}

// TestListAuditLogReadsTheRetentionBound covers the rule that the viewer never
// asks for more rows than the server keeps and reports that same bound.
func TestListAuditLogReadsTheRetentionBound(t *testing.T) {
	stub := &auditStubRepository{}
	service := NewAdminService(stub)
	entries, limit, err := service.ListAuditLog()
	if err != nil {
		t.Fatal(err)
	}
	if stub.limit != domain.AdminAuditRetentionLimit || limit != domain.AdminAuditRetentionLimit {
		t.Fatalf("audit read bound = %d / reported %d, want %d", stub.limit, limit, domain.AdminAuditRetentionLimit)
	}
	if entries == nil || len(entries) != 0 {
		t.Fatalf("empty audit log must return an empty slice, got %#v", entries)
	}
}

func TestListAuditLogWrapsRepositoryError(t *testing.T) {
	failure := errors.New("audit store unavailable")
	service := NewAdminService(&auditStubRepository{err: failure})
	if _, _, err := service.ListAuditLog(); !errors.Is(err, failure) {
		t.Fatalf("repository error was not wrapped: %v", err)
	}
}
