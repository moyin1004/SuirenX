package service

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

var ErrInvalidAdminInput = errors.New("invalid admin input")

type AdminService struct {
	repository repository.AdminRepository
	now        func() time.Time
}

type ExpiryService struct {
	repository repository.ExpiryReadRepository
}

func NewExpiryService(repository repository.ExpiryReadRepository) *ExpiryService {
	return &ExpiryService{repository: repository}
}

func (s *ExpiryService) ListForOwner(ownerID string) ([]domain.VersionedExpiryItem, error) {
	if strings.TrimSpace(ownerID) == "" {
		return nil, ErrInvalidAdminInput
	}
	items, err := s.repository.ListExpiryForOwner(ownerID)
	if err != nil {
		return nil, fmt.Errorf("list expiry items: %w", err)
	}
	if items == nil {
		items = []domain.VersionedExpiryItem{}
	}
	return items, nil
}

func NewAdminService(repository repository.AdminRepository) *AdminService {
	return &AdminService{repository: repository, now: time.Now}
}

func (s *AdminService) ListAccounts() ([]domain.AdminAccount, error) {
	accounts, err := s.repository.ListAccounts()
	if err != nil {
		return nil, fmt.Errorf("list accounts: %w", err)
	}
	if accounts == nil {
		accounts = []domain.AdminAccount{}
	}
	return accounts, nil
}

func (s *AdminService) SetAccountDisabled(accountID, actorID string, disabled bool) error {
	accountID, actorID = strings.TrimSpace(accountID), strings.TrimSpace(actorID)
	if accountID == "" || actorID == "" {
		return ErrInvalidAdminInput
	}
	if err := s.repository.SetAccountDisabled(accountID, actorID, disabled, s.now().UTC()); err != nil {
		return fmt.Errorf("update account state: %w", err)
	}
	return nil
}

func (s *AdminService) RecordAudit(actorID, targetType, targetID, action string) error {
	if actorID == "" || targetType == "" || targetID == "" || action == "" {
		return ErrInvalidAdminInput
	}
	if err := s.repository.RecordAudit(actorID, targetType, targetID, action, s.now().UTC()); err != nil {
		return fmt.Errorf("record admin action: %w", err)
	}
	return nil
}
