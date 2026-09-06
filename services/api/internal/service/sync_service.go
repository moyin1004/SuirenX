package service

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

var (
	ErrInvalidSyncRequest = errors.New("invalid sync request")
	ErrSyncConflict       = errors.New("sync conflict")
)

type SyncAssetInput struct {
	ID              string   `json:"id"`
	BaseVersion     int64    `json:"base_version"`
	Deleted         bool     `json:"deleted"`
	Name            string   `json:"name"`
	PriceCents      int64    `json:"price_cents"`
	PurchaseDate    string   `json:"purchase_date"`
	Status          string   `json:"status"`
	ImageURL        string   `json:"image_url"`
	RetiredDate     string   `json:"retired_date"`
	ArchivedAt      string   `json:"archived_at"`
	IconKey         string   `json:"icon_key"`
	PurchaseChannel string   `json:"purchase_channel"`
	WarrantyEndDate string   `json:"warranty_end_date"`
	Notes           string   `json:"notes"`
	Tags            []string `json:"tags"`
}

type SyncExpiryInput struct {
	ID                 string `json:"id"`
	BaseVersion        int64  `json:"base_version"`
	Deleted            bool   `json:"deleted"`
	Name               string `json:"name"`
	Category           string `json:"category"`
	PackageExpiryDate  string `json:"package_expiry_date"`
	OpenedDate         string `json:"opened_date"`
	OpenedValidityDays int    `json:"opened_validity_days"`
	Location           string `json:"location"`
	Notes              string `json:"notes"`
	Status             string `json:"status"`
	ArchivedAt         string `json:"archived_at"`
}

type SyncBatchInput struct {
	Cursor         int64             `json:"cursor"`
	ExpiryCursor   int64             `json:"expiry_cursor"`
	IdempotencyKey string            `json:"idempotency_key"`
	Changes        []SyncAssetInput  `json:"changes"`
	ExpiryChanges  []SyncExpiryInput `json:"expiry_changes"`
}

type SyncService struct {
	repository repository.SyncRepository
	now        func() time.Time
}

func NewSyncService(repository repository.SyncRepository) *SyncService {
	return &SyncService{repository: repository, now: time.Now}
}

func (s *SyncService) Apply(ownerID string, input SyncBatchInput) (domain.SyncBatchResult, error) {
	if ownerID == "" || input.Cursor < 0 || input.ExpiryCursor < 0 || strings.TrimSpace(input.IdempotencyKey) == "" || len(input.IdempotencyKey) > 128 || len(input.Changes)+len(input.ExpiryChanges) > 100 {
		return domain.SyncBatchResult{}, ErrInvalidSyncRequest
	}
	seen := make(map[string]struct{}, len(input.Changes))
	changes := make([]repository.SyncAssetInput, 0, len(input.Changes))
	for _, item := range input.Changes {
		if err := validateSyncAsset(item, nowDate(s.now)); err != nil {
			return domain.SyncBatchResult{}, err
		}
		if _, exists := seen[item.ID]; exists {
			return domain.SyncBatchResult{}, ErrInvalidSyncRequest
		}
		seen[item.ID] = struct{}{}
		changes = append(changes, repository.SyncAssetInput(item))
	}
	seenExpiry := make(map[string]struct{}, len(input.ExpiryChanges))
	expiryChanges := make([]repository.SyncExpiryInput, 0, len(input.ExpiryChanges))
	for _, item := range input.ExpiryChanges {
		if err := validateSyncExpiry(item); err != nil {
			return domain.SyncBatchResult{}, err
		}
		if _, exists := seenExpiry[item.ID]; exists {
			return domain.SyncBatchResult{}, ErrInvalidSyncRequest
		}
		seenExpiry[item.ID] = struct{}{}
		expiryChanges = append(expiryChanges, repository.SyncExpiryInput(item))
	}
	now := s.now().UTC()
	result, err := s.repository.ApplyBatch(ownerID, input.Cursor, input.IdempotencyKey, changes, now)
	if err != nil {
		return domain.SyncBatchResult{}, fmt.Errorf("apply sync batch: %w", err)
	}
	expiryResult, err := s.repository.ApplyExpiryBatch(ownerID, input.ExpiryCursor, input.IdempotencyKey, expiryChanges, now)
	if err != nil {
		return domain.SyncBatchResult{}, fmt.Errorf("apply expiry sync batch: %w", err)
	}
	result.NextExpiryCursor = expiryResult.NextExpiryCursor
	result.AppliedExpiry = append(result.AppliedExpiry, expiryResult.AppliedExpiry...)
	result.ExpiryChanges = append(result.ExpiryChanges, expiryResult.ExpiryChanges...)
	result.ExpiryConflicts = append(result.ExpiryConflicts, expiryResult.ExpiryConflicts...)
	ensureSyncArrays(&result)
	return result, nil
}

func validateSyncAsset(item SyncAssetInput, today time.Time) error {
	if item.ID == "" || item.BaseVersion < 0 || item.PriceCents < 0 {
		return ErrInvalidSyncRequest
	}
	if item.Deleted {
		return nil
	}
	if strings.TrimSpace(item.Name) == "" {
		return ErrInvalidSyncRequest
	}
	purchase, err := time.Parse(time.DateOnly, item.PurchaseDate)
	if err != nil {
		return ErrInvalidSyncRequest
	}
	if item.Status != "ACTIVE" && item.Status != "RETIRED" {
		return ErrInvalidSyncRequest
	}
	if item.Status == "ACTIVE" && item.RetiredDate != "" {
		return ErrInvalidSyncRequest
	}
	if item.Status == "RETIRED" {
		retired, parseErr := time.Parse(time.DateOnly, item.RetiredDate)
		if parseErr != nil || retired.Before(purchase) || retired.After(today) {
			return ErrInvalidSyncRequest
		}
	}
	if item.WarrantyEndDate != "" {
		if _, err := time.Parse(time.DateOnly, item.WarrantyEndDate); err != nil {
			return ErrInvalidSyncRequest
		}
	}
	if item.ArchivedAt != "" {
		if _, err := time.Parse(time.RFC3339, item.ArchivedAt); err != nil {
			return ErrInvalidSyncRequest
		}
	}
	if len(item.Notes) > 2000 || len(item.Tags) > 20 {
		return ErrInvalidSyncRequest
	}
	for _, tag := range item.Tags {
		if len(tag) > 30 {
			return ErrInvalidSyncRequest
		}
	}
	return nil
}

func nowDate(clock func() time.Time) time.Time {
	now := clock().UTC()
	return time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
}

func validateSyncExpiry(item SyncExpiryInput) error {
	if item.ID == "" || item.BaseVersion < 0 {
		return ErrInvalidSyncRequest
	}
	if item.Deleted {
		return nil
	}
	if strings.TrimSpace(item.Name) == "" || strings.TrimSpace(item.Category) == "" || strings.TrimSpace(item.Location) == "" {
		return ErrInvalidSyncRequest
	}
	if _, err := time.Parse(time.DateOnly, item.PackageExpiryDate); err != nil {
		return ErrInvalidSyncRequest
	}
	if item.OpenedDate == "" && item.OpenedValidityDays != 0 {
		return ErrInvalidSyncRequest
	}
	if item.OpenedDate != "" {
		if _, err := time.Parse(time.DateOnly, item.OpenedDate); err != nil || item.OpenedValidityDays <= 0 {
			return ErrInvalidSyncRequest
		}
	}
	switch item.Status {
	case "IN_USE", "USED_UP", "DISCARDED":
	default:
		return ErrInvalidSyncRequest
	}
	if item.ArchivedAt != "" {
		if _, err := time.Parse(time.RFC3339, item.ArchivedAt); err != nil {
			return ErrInvalidSyncRequest
		}
	}
	if len([]rune(item.Name)) > 200 || len([]rune(item.Category)) > 100 || len([]rune(item.Location)) > 200 || len([]rune(item.Notes)) > 2000 {
		return ErrInvalidSyncRequest
	}
	return nil
}

func ensureSyncArrays(result *domain.SyncBatchResult) {
	if result.Applied == nil {
		result.Applied = []domain.SyncChange{}
	}
	if result.Changes == nil {
		result.Changes = []domain.SyncChange{}
	}
	if result.Conflicts == nil {
		result.Conflicts = []domain.SyncConflict{}
	}
	if result.AppliedExpiry == nil {
		result.AppliedExpiry = []domain.SyncExpiryChange{}
	}
	if result.ExpiryChanges == nil {
		result.ExpiryChanges = []domain.SyncExpiryChange{}
	}
	if result.ExpiryConflicts == nil {
		result.ExpiryConflicts = []domain.SyncExpiryConflict{}
	}
}
