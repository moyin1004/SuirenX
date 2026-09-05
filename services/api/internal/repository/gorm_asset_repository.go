package repository

import (
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"gorm.io/gorm"
)

type AssetRecord struct {
	ID           string `gorm:"primaryKey;size:32"`
	Name         string `gorm:"not null"`
	PriceCents   int64  `gorm:"not null"`
	PurchaseDate time.Time
	RetiredAt    *time.Time
	Status       string `gorm:"index;not null"`
	ImageURL     string
	CreatedAt    time.Time
	UpdatedAt    time.Time
}

type GormAssetRepository struct {
	db *gorm.DB
}

func NewGormAssetRepository(db *gorm.DB) *GormAssetRepository {
	return &GormAssetRepository{db: db}
}

func (r *GormAssetRepository) List(status *domain.AssetStatus) ([]domain.Asset, error) {
	query := r.db.Order("created_at DESC")
	if status != nil {
		query = query.Where("status = ?", string(*status))
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

func (r *GormAssetRepository) Create(asset *domain.Asset) error {
	record := recordFromDomain(*asset)
	if err := r.db.Create(&record).Error; err != nil {
		return err
	}
	asset.CreatedAt = record.CreatedAt
	asset.UpdatedAt = record.UpdatedAt
	return nil
}

func (r *GormAssetRepository) Update(asset *domain.Asset) error {
	result := r.db.Model(&AssetRecord{}).Where("id = ?", asset.ID).
		Updates(map[string]interface{}{
			"name":          asset.Name,
			"price_cents":   asset.PriceCents,
			"purchase_date": asset.PurchaseDate,
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

func (r *GormAssetRepository) Count() (int64, error) {
	var count int64
	err := r.db.Model(&AssetRecord{}).Count(&count).Error
	return count, err
}

func (r AssetRecord) toDomain() domain.Asset {
	return domain.Asset{
		ID:           r.ID,
		Name:         r.Name,
		PriceCents:   r.PriceCents,
		PurchaseDate: r.PurchaseDate,
		RetiredAt:    r.RetiredAt,
		Status:       domain.AssetStatus(r.Status),
		ImageURL:     r.ImageURL,
		CreatedAt:    r.CreatedAt,
		UpdatedAt:    r.UpdatedAt,
	}
}

func recordFromDomain(asset domain.Asset) AssetRecord {
	return AssetRecord{
		ID:           asset.ID,
		Name:         asset.Name,
		PriceCents:   asset.PriceCents,
		PurchaseDate: asset.PurchaseDate,
		RetiredAt:    asset.RetiredAt,
		Status:       string(asset.Status),
		ImageURL:     asset.ImageURL,
		CreatedAt:    asset.CreatedAt,
		UpdatedAt:    asset.UpdatedAt,
	}
}
