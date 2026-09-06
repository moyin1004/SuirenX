package repository

import (
	"encoding/json"
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"gorm.io/gorm"
)

type SyncAssetInput struct {
	ID              string
	BaseVersion     int64
	Deleted         bool
	Name            string
	PriceCents      int64
	PurchaseDate    string
	Status          string
	ImageURL        string
	RetiredDate     string
	ArchivedAt      string
	IconKey         string
	PurchaseChannel string
	WarrantyEndDate string
	Notes           string
	Tags            []string
}

type SyncRepository interface {
	ApplyBatch(ownerID string, cursor int64, idempotencyKey string, changes []SyncAssetInput, now time.Time) (domain.SyncBatchResult, error)
	ApplyExpiryBatch(ownerID string, cursor int64, idempotencyKey string, changes []SyncExpiryInput, now time.Time) (domain.SyncBatchResult, error)
}

type SyncEventRecord struct {
	ID           int64  `gorm:"primaryKey;autoIncrement"`
	OwnerID      string `gorm:"not null;index:idx_sync_events_owner_cursor,priority:1"`
	AssetID      string `gorm:"not null;index:idx_sync_events_owner_cursor,priority:2"`
	Version      int64  `gorm:"not null"`
	DeletedAt    *time.Time
	SnapshotJSON string    `gorm:"not null"`
	CreatedAt    time.Time `gorm:"not null"`
}

type SyncIdempotencyRecord struct {
	OwnerID        string    `gorm:"primaryKey"`
	IdempotencyKey string    `gorm:"primaryKey"`
	ResponseJSON   string    `gorm:"not null"`
	CreatedAt      time.Time `gorm:"not null"`
}

type SyncExpiryInput struct {
	ID                 string
	BaseVersion        int64
	Deleted            bool
	Name               string
	Category           string
	PackageExpiryDate  string
	OpenedDate         string
	OpenedValidityDays int
	Location           string
	Notes              string
	Status             string
	ArchivedAt         string
}

type ExpiryRecord struct {
	ID                 string `gorm:"primaryKey"`
	OwnerID            string `gorm:"not null;index"`
	Name               string `gorm:"not null"`
	Category           string `gorm:"not null"`
	PackageExpiryDate  string `gorm:"not null"`
	OpenedDate         string
	OpenedValidityDays int
	Location           string `gorm:"not null"`
	Notes              string `gorm:"not null"`
	Status             string `gorm:"not null"`
	ArchivedAt         *time.Time
	Version            int64 `gorm:"not null;default:1"`
	DeletedAt          *time.Time
	CreatedAt          time.Time `gorm:"not null"`
	UpdatedAt          time.Time `gorm:"not null"`
}

type SyncExpiryEventRecord struct {
	ID           int64  `gorm:"primaryKey;autoIncrement"`
	OwnerID      string `gorm:"not null;index:idx_sync_expiry_events_owner_cursor,priority:1"`
	ExpiryID     string `gorm:"not null;index:idx_sync_expiry_events_owner_cursor,priority:2"`
	Version      int64  `gorm:"not null"`
	DeletedAt    *time.Time
	SnapshotJSON string    `gorm:"not null"`
	CreatedAt    time.Time `gorm:"not null"`
}

func (ExpiryRecord) TableName() string          { return "expiry_records" }
func (SyncExpiryEventRecord) TableName() string { return "sync_expiry_events" }

func (SyncEventRecord) TableName() string       { return "sync_events" }
func (SyncIdempotencyRecord) TableName() string { return "sync_idempotency" }

func (r *GormAssetRepository) ApplyBatch(ownerID string, cursor int64, idempotencyKey string, changes []SyncAssetInput, now time.Time) (domain.SyncBatchResult, error) {
	var result domain.SyncBatchResult
	err := r.db.Transaction(func(tx *gorm.DB) error {
		var cached SyncIdempotencyRecord
		if err := tx.Where("owner_id = ? AND idempotency_key = ?", ownerID, idempotencyKey).First(&cached).Error; err == nil {
			return json.Unmarshal([]byte(cached.ResponseJSON), &result)
		} else if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}

		for _, input := range changes {
			var record AssetRecord
			err := tx.Where("id = ? AND owner_id = ?", input.ID, ownerID).First(&record).Error
			if errors.Is(err, gorm.ErrRecordNotFound) {
				var other AssetRecord
				if otherErr := tx.Where("id = ?", input.ID).First(&other).Error; otherErr == nil {
					// Do not reveal another owner's version or snapshot. UUID collisions
					// are treated as an ownership conflict with an opaque response.
					result.Conflicts = append(result.Conflicts, domain.SyncConflict{ID: input.ID, BaseVersion: input.BaseVersion})
					continue
				} else if !errors.Is(otherErr, gorm.ErrRecordNotFound) {
					return otherErr
				}
				if input.BaseVersion != 0 {
					continue
				}
				record = AssetRecord{ID: input.ID, OwnerID: ownerID, Version: 1, CreatedAt: now, UpdatedAt: now}
				applyInput(&record, input, now)
				if err := tx.Create(&record).Error; err != nil {
					return err
				}
			} else if err != nil {
				return err
			} else {
				if input.BaseVersion != record.Version {
					result.Conflicts = append(result.Conflicts, domain.SyncConflict{ID: input.ID, BaseVersion: input.BaseVersion, RemoteVersion: record.Version, Remote: snapshot(record)})
					continue
				}
				record.Version++
				record.UpdatedAt = now
				applyInput(&record, input, now)
				if err := tx.Save(&record).Error; err != nil {
					return err
				}
			}

			event := SyncEventRecord{
				OwnerID: ownerID, AssetID: record.ID, Version: record.Version,
				DeletedAt: record.DeletedAt, SnapshotJSON: mustJSON(snapshot(record)), CreatedAt: now,
			}
			if err := tx.Create(&event).Error; err != nil {
				return err
			}
			change := domain.SyncChange{Cursor: event.ID, Version: record.Version, DeletedAt: formatTimestamp(record.DeletedAt), Asset: snapshot(record)}
			result.Applied = append(result.Applied, change)
		}

		var events []SyncEventRecord
		if err := tx.Where("owner_id = ? AND id > ?", ownerID, cursor).Order("id ASC").Find(&events).Error; err != nil {
			return err
		}
		for _, event := range events {
			result.Changes = append(result.Changes, domain.SyncChange{Cursor: event.ID, Version: event.Version, DeletedAt: formatTimestamp(event.DeletedAt), Asset: parseSnapshot(event.SnapshotJSON)})
			if event.ID > result.NextCursor {
				result.NextCursor = event.ID
			}
		}
		if result.NextCursor < cursor {
			result.NextCursor = cursor
		}
		encoded, err := json.Marshal(result)
		if err != nil {
			return err
		}
		return tx.Create(&SyncIdempotencyRecord{OwnerID: ownerID, IdempotencyKey: idempotencyKey, ResponseJSON: string(encoded), CreatedAt: now}).Error
	})
	return result, err
}

func applyInput(record *AssetRecord, input SyncAssetInput, now time.Time) {
	record.Name = input.Name
	record.PriceCents = input.PriceCents
	record.PurchaseDate, _ = time.Parse(time.DateOnly, input.PurchaseDate)
	record.Status = input.Status
	record.ImageURL = input.ImageURL
	record.IconKey = input.IconKey
	record.PurchaseChannel = input.PurchaseChannel
	record.WarrantyEndDate = parseDate(input.WarrantyEndDate)
	record.Notes = input.Notes
	record.TagsJSON = encodeTags(input.Tags)
	record.RetiredAt = parseDate(input.RetiredDate)
	record.ArchivedAt = parseTimestamp(input.ArchivedAt)
	if input.Deleted {
		record.DeletedAt = &now
	} else {
		record.DeletedAt = nil
	}
	if record.CreatedAt.IsZero() {
		record.CreatedAt = now
	}
	record.UpdatedAt = now
}

func snapshot(record AssetRecord) domain.SyncAsset {
	return domain.SyncAsset{ID: record.ID, Name: record.Name, PriceCents: record.PriceCents, PurchaseDate: formatDate(record.PurchaseDate), Status: record.Status, ImageURL: record.ImageURL, RetiredDate: formatDatePtr(record.RetiredAt), ArchivedAt: formatTimestamp(record.ArchivedAt), IconKey: record.IconKey, PurchaseChannel: record.PurchaseChannel, WarrantyEndDate: formatDatePtr(record.WarrantyEndDate), Notes: record.Notes, Tags: decodeTags(record.TagsJSON)}
}

func mustJSON(value domain.SyncAsset) string {
	encoded, _ := json.Marshal(value)
	return string(encoded)
}
func parseSnapshot(value string) domain.SyncAsset {
	var result domain.SyncAsset
	_ = json.Unmarshal([]byte(value), &result)
	return result
}
func parseDate(value string) *time.Time {
	if value == "" {
		return nil
	}
	parsed, err := time.Parse(time.DateOnly, value)
	if err != nil {
		return nil
	}
	return &parsed
}
func parseTimestamp(value string) *time.Time {
	if value == "" {
		return nil
	}
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		return nil
	}
	return &parsed
}
func formatDate(value time.Time) string {
	if value.IsZero() {
		return ""
	}
	return value.Format(time.DateOnly)
}
func formatDatePtr(value *time.Time) string {
	if value == nil {
		return ""
	}
	return value.Format(time.DateOnly)
}
func formatTimestamp(value *time.Time) string {
	if value == nil {
		return ""
	}
	return value.Format(time.RFC3339)
}

func (r *GormAssetRepository) ApplyExpiryBatch(ownerID string, cursor int64, idempotencyKey string, changes []SyncExpiryInput, now time.Time) (domain.SyncBatchResult, error) {
	var result domain.SyncBatchResult
	err := r.db.Transaction(func(tx *gorm.DB) error {
		cachedKey := idempotencyKey + ":expiry"
		var cached SyncIdempotencyRecord
		if err := tx.Where("owner_id = ? AND idempotency_key = ?", ownerID, cachedKey).First(&cached).Error; err == nil {
			return json.Unmarshal([]byte(cached.ResponseJSON), &result)
		} else if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		for _, input := range changes {
			var record ExpiryRecord
			err := tx.Where("id = ? AND owner_id = ?", input.ID, ownerID).First(&record).Error
			if errors.Is(err, gorm.ErrRecordNotFound) {
				var other ExpiryRecord
				if otherErr := tx.Where("id = ?", input.ID).First(&other).Error; otherErr == nil {
					result.ExpiryConflicts = append(result.ExpiryConflicts, domain.SyncExpiryConflict{ID: input.ID, BaseVersion: input.BaseVersion})
					continue
				} else if !errors.Is(otherErr, gorm.ErrRecordNotFound) {
					return otherErr
				}
				if input.BaseVersion != 0 {
					continue
				}
				record = ExpiryRecord{ID: input.ID, OwnerID: ownerID, Version: 1, CreatedAt: now, UpdatedAt: now}
				applyExpiryInput(&record, input, now)
				if err := tx.Create(&record).Error; err != nil {
					return err
				}
			} else if err != nil {
				return err
			} else {
				if input.BaseVersion != record.Version {
					result.ExpiryConflicts = append(result.ExpiryConflicts, domain.SyncExpiryConflict{ID: input.ID, BaseVersion: input.BaseVersion, RemoteVersion: record.Version, Remote: expirySnapshot(record)})
					continue
				}
				record.Version++
				record.UpdatedAt = now
				applyExpiryInput(&record, input, now)
				if err := tx.Save(&record).Error; err != nil {
					return err
				}
			}
			event := SyncExpiryEventRecord{OwnerID: ownerID, ExpiryID: record.ID, Version: record.Version, DeletedAt: record.DeletedAt, SnapshotJSON: mustJSONExpiry(expirySnapshot(record)), CreatedAt: now}
			if err := tx.Create(&event).Error; err != nil {
				return err
			}
			result.AppliedExpiry = append(result.AppliedExpiry, domain.SyncExpiryChange{Cursor: event.ID, Version: record.Version, DeletedAt: formatTimestamp(record.DeletedAt), Item: expirySnapshot(record)})
		}
		var events []SyncExpiryEventRecord
		if err := tx.Where("owner_id = ? AND id > ?", ownerID, cursor).Order("id ASC").Find(&events).Error; err != nil {
			return err
		}
		for _, event := range events {
			result.ExpiryChanges = append(result.ExpiryChanges, domain.SyncExpiryChange{Cursor: event.ID, Version: event.Version, DeletedAt: formatTimestamp(event.DeletedAt), Item: parseExpirySnapshot(event.SnapshotJSON)})
			if event.ID > result.NextExpiryCursor {
				result.NextExpiryCursor = event.ID
			}
		}
		if result.NextExpiryCursor < cursor {
			result.NextExpiryCursor = cursor
		}
		encoded, err := json.Marshal(result)
		if err != nil {
			return err
		}
		return tx.Create(&SyncIdempotencyRecord{OwnerID: ownerID, IdempotencyKey: cachedKey, ResponseJSON: string(encoded), CreatedAt: now}).Error
	})
	return result, err
}

func applyExpiryInput(record *ExpiryRecord, input SyncExpiryInput, now time.Time) {
	record.Name, record.Category, record.PackageExpiryDate = input.Name, input.Category, input.PackageExpiryDate
	record.OpenedDate, record.OpenedValidityDays = input.OpenedDate, input.OpenedValidityDays
	record.Location, record.Notes, record.Status = input.Location, input.Notes, input.Status
	record.ArchivedAt = parseTimestamp(input.ArchivedAt)
	if input.Deleted {
		record.DeletedAt = &now
	} else {
		record.DeletedAt = nil
	}
	if record.CreatedAt.IsZero() {
		record.CreatedAt = now
	}
	record.UpdatedAt = now
}

func expirySnapshot(record ExpiryRecord) domain.SyncExpiryItem {
	return domain.SyncExpiryItem{ID: record.ID, Name: record.Name, Category: record.Category, PackageExpiryDate: record.PackageExpiryDate, OpenedDate: record.OpenedDate, OpenedValidityDays: record.OpenedValidityDays, Location: record.Location, Notes: record.Notes, Status: record.Status, ArchivedAt: formatTimestamp(record.ArchivedAt)}
}

func mustJSONExpiry(value domain.SyncExpiryItem) string {
	encoded, _ := json.Marshal(value)
	return string(encoded)
}
func parseExpirySnapshot(value string) domain.SyncExpiryItem {
	var result domain.SyncExpiryItem
	_ = json.Unmarshal([]byte(value), &result)
	return result
}
