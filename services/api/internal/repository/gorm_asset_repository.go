package repository

import (
	"encoding/json"
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"gorm.io/gorm"
)

type AssetRecord struct {
	ID              string `gorm:"primaryKey;size:32"`
	OwnerID         string `gorm:"index"`
	Name            string `gorm:"not null"`
	PriceCents      int64  `gorm:"not null"`
	PurchaseDate    time.Time
	RetiredAt       *time.Time
	ArchivedAt      *time.Time `gorm:"index"`
	Status          string     `gorm:"index;not null"`
	ImageURL        string
	IconKey         string
	PurchaseChannel string
	WarrantyEndDate *time.Time
	Notes           string
	TagsJSON        string
	CreatedAt       time.Time
	UpdatedAt       time.Time
	Version         int64 `gorm:"not null;default:1"`
	DeletedAt       *time.Time
}

type GormAssetRepository struct {
	db *gorm.DB
}

func NewGormAssetRepository(db *gorm.DB) *GormAssetRepository {
	return &GormAssetRepository{db: db}
}

func (r *GormAssetRepository) List(status *domain.AssetStatus, archived *bool) ([]domain.Asset, error) {
	query := r.db.Order("created_at DESC")
	if status != nil {
		query = query.Where("status = ?", string(*status))
	}

	if archived != nil {
		if *archived {
			query = query.Where("archived_at IS NOT NULL")
		} else {
			query = query.Where("archived_at IS NULL")
		}
	}
	var records []AssetRecord
	if err := query.Find(&records).Error; err != nil {
		return nil, err
	}

	assets := make([]domain.Asset, 0, len(records))
	for _, record := range records {
		assets = append(assets, record.toDomain())
	}
	return assets, nil
}

func (r *GormAssetRepository) ListForOwner(ownerID string, status *domain.AssetStatus, archived *bool) ([]domain.Asset, error) {
	query := r.db.Where("owner_id = ?", ownerID).Order("created_at DESC")
	if status != nil {
		query = query.Where("status = ?", string(*status))
	}
	if archived != nil {
		if *archived {
			query = query.Where("archived_at IS NOT NULL")
		} else {
			query = query.Where("archived_at IS NULL")
		}
	}
	var records []AssetRecord
	if err := query.Find(&records).Error; err != nil {
		return nil, err
	}
	assets := make([]domain.Asset, 0, len(records))
	for _, record := range records {
		assets = append(assets, record.toDomain())
	}
	return assets, nil
}

func (r *GormAssetRepository) Get(id string) (*domain.Asset, error) {
	var record AssetRecord
	err := r.db.Where("id = ?", id).First(&record).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	asset := record.toDomain()
	return &asset, nil
}

func (r *GormAssetRepository) GetForOwner(ownerID, id string) (*domain.Asset, error) {
	var record AssetRecord
	err := r.db.Where("id = ? AND owner_id = ?", id, ownerID).First(&record).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	asset := record.toDomain()
	return &asset, nil
}

func (r *GormAssetRepository) Create(asset *domain.Asset) error {
	record := recordFromDomain(*asset)
	if err := r.db.Create(&record).Error; err != nil {
		return err
	}
	asset.CreatedAt = record.CreatedAt
	asset.UpdatedAt = record.UpdatedAt
	return nil
}

func (r *GormAssetRepository) CreateForOwner(ownerID string, asset *domain.Asset) error {
	asset.OwnerID = ownerID
	record := recordFromDomain(*asset)
	record.OwnerID = ownerID
	record.Version = 1
	if err := r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&record).Error; err != nil {
			return err
		}
		return appendAssetSyncEvent(tx, ownerID, record, record.CreatedAt)
	}); err != nil {
		return err
	}
	*asset = record.toDomain()
	return nil
}

func (r *GormAssetRepository) Update(asset *domain.Asset) error {
	result := r.db.Model(&AssetRecord{}).Where("id = ?", asset.ID).
		Updates(map[string]interface{}{
			"name":              asset.Name,
			"price_cents":       asset.PriceCents,
			"purchase_date":     asset.PurchaseDate,
			"icon_key":          asset.IconKey,
			"purchase_channel":  asset.PurchaseChannel,
			"warranty_end_date": asset.WarrantyEndDate,
			"notes":             asset.Notes,
			"tags_json":         encodeTags(asset.Tags),
		})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return ErrNotFound
	}
	fresh, err := r.Get(asset.ID)
	if err != nil {
		return err
	}
	*asset = *fresh
	return nil
}

func (r *GormAssetRepository) UpdateForOwner(ownerID string, asset *domain.Asset) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var record AssetRecord
		if err := tx.Where("id = ? AND owner_id = ?", asset.ID, ownerID).First(&record).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrNotFound
		} else if err != nil {
			return err
		}
		record.Name, record.PriceCents, record.PurchaseDate = asset.Name, asset.PriceCents, asset.PurchaseDate
		record.IconKey, record.PurchaseChannel, record.WarrantyEndDate = asset.IconKey, asset.PurchaseChannel, asset.WarrantyEndDate
		record.Notes, record.TagsJSON = asset.Notes, encodeTags(asset.Tags)
		record.Version++
		record.UpdatedAt = time.Now().UTC()
		if err := tx.Save(&record).Error; err != nil {
			return err
		}
		if err := appendAssetSyncEvent(tx, ownerID, record, record.UpdatedAt); err != nil {
			return err
		}
		*asset = record.toDomain()
		return nil
	})
}

// UpdateStatus writes both lifecycle fields together, including a NULL retirement
// date on reactivation. Ordinary editable fields are preserved.
func (r *GormAssetRepository) UpdateStatus(asset *domain.Asset) error {
	result := r.db.Model(&AssetRecord{}).Where("id = ?", asset.ID).
		Updates(map[string]interface{}{"status": string(asset.Status), "retired_at": asset.RetiredAt})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return ErrNotFound
	}
	fresh, err := r.Get(asset.ID)
	if err != nil {
		return err
	}
	*asset = *fresh
	return nil
}

func (r *GormAssetRepository) UpdateStatusForOwner(ownerID string, asset *domain.Asset) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var record AssetRecord
		if err := tx.Where("id = ? AND owner_id = ?", asset.ID, ownerID).First(&record).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrNotFound
		} else if err != nil {
			return err
		}
		record.Status, record.RetiredAt = string(asset.Status), asset.RetiredAt
		record.Version++
		record.UpdatedAt = time.Now().UTC()
		if err := tx.Save(&record).Error; err != nil {
			return err
		}
		if err := appendAssetSyncEvent(tx, ownerID, record, record.UpdatedAt); err != nil {
			return err
		}
		*asset = record.toDomain()
		return nil
	})
}

func (r *GormAssetRepository) Count() (int64, error) {
	var count int64
	err := r.db.Model(&AssetRecord{}).Count(&count).Error
	return count, err
}

func (r AssetRecord) toDomain() domain.Asset {
	return domain.Asset{
		ID:              r.ID,
		OwnerID:         r.OwnerID,
		Name:            r.Name,
		PriceCents:      r.PriceCents,
		PurchaseDate:    r.PurchaseDate,
		RetiredAt:       r.RetiredAt,
		ArchivedAt:      r.ArchivedAt,
		Status:          domain.AssetStatus(r.Status),
		ImageURL:        r.ImageURL,
		IconKey:         r.IconKey,
		PurchaseChannel: r.PurchaseChannel,
		WarrantyEndDate: r.WarrantyEndDate,
		Notes:           r.Notes,
		Tags:            decodeTags(r.TagsJSON),
		CreatedAt:       r.CreatedAt,
		UpdatedAt:       r.UpdatedAt,
	}
}

func recordFromDomain(asset domain.Asset) AssetRecord {
	return AssetRecord{
		ID:              asset.ID,
		Name:            asset.Name,
		PriceCents:      asset.PriceCents,
		PurchaseDate:    asset.PurchaseDate,
		RetiredAt:       asset.RetiredAt,
		ArchivedAt:      asset.ArchivedAt,
		Status:          string(asset.Status),
		ImageURL:        asset.ImageURL,
		IconKey:         asset.IconKey,
		PurchaseChannel: asset.PurchaseChannel,
		WarrantyEndDate: asset.WarrantyEndDate,
		Notes:           asset.Notes,
		TagsJSON:        encodeTags(asset.Tags),
		CreatedAt:       asset.CreatedAt,
		UpdatedAt:       asset.UpdatedAt,
	}
}

func encodeTags(tags []string) string {
	encoded, err := json.Marshal(tags)
	if err != nil {
		return "[]"
	}
	return string(encoded)
}

func decodeTags(encoded string) []string {
	if encoded == "" {
		return []string{}
	}
	var tags []string
	if err := json.Unmarshal([]byte(encoded), &tags); err != nil || tags == nil {
		return []string{}
	}
	return tags
}

// Archive transitions only affect visibility. Preserve the first archive time on
// retries; restoring an already-current asset is a no-op.
func (r *GormAssetRepository) UpdateArchive(asset *domain.Asset) error {
	query := r.db.Model(&AssetRecord{}).Where("id = ?", asset.ID)
	if asset.ArchivedAt != nil {
		query = query.Where("archived_at IS NULL")
	} else {
		query = query.Where("archived_at IS NOT NULL")
	}
	if err := query.Updates(map[string]interface{}{"archived_at": asset.ArchivedAt}).Error; err != nil {
		return err
	}
	fresh, err := r.Get(asset.ID)
	if err != nil {
		return err
	}
	*asset = *fresh
	return nil
}

func (r *GormAssetRepository) UpdateArchiveForOwner(ownerID string, asset *domain.Asset) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var record AssetRecord
		if err := tx.Where("id = ? AND owner_id = ?", asset.ID, ownerID).First(&record).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrNotFound
		} else if err != nil {
			return err
		}
		alreadyCurrent := (record.ArchivedAt == nil && asset.ArchivedAt == nil) || (record.ArchivedAt != nil && asset.ArchivedAt != nil)
		if !alreadyCurrent {
			record.ArchivedAt = asset.ArchivedAt
			record.Version++
			record.UpdatedAt = time.Now().UTC()
			if err := tx.Save(&record).Error; err != nil {
				return err
			}
			if err := appendAssetSyncEvent(tx, ownerID, record, record.UpdatedAt); err != nil {
				return err
			}
		}
		*asset = record.toDomain()
		return nil
	})
}

func appendAssetSyncEvent(tx *gorm.DB, ownerID string, record AssetRecord, createdAt time.Time) error {
	return tx.Create(&SyncEventRecord{
		OwnerID: ownerID, AssetID: record.ID, Version: record.Version,
		DeletedAt: record.DeletedAt, SnapshotJSON: mustJSON(snapshot(record)), CreatedAt: createdAt,
	}).Error
}
