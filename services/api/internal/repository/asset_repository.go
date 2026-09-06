package repository

import (
	"errors"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
)

// ErrNotFound is returned when no asset matches the requested id.
var ErrNotFound = errors.New("asset not found")

type AssetRepository interface {
	List(status *domain.AssetStatus, archived *bool) ([]domain.Asset, error)
	Get(id string) (*domain.Asset, error)
	Create(asset *domain.Asset) error
	Update(asset *domain.Asset) error
	UpdateStatus(asset *domain.Asset) error
	UpdateArchive(asset *domain.Asset) error
	Count() (int64, error)
}

type OwnerAssetRepository interface {
	ListForOwner(ownerID string, status *domain.AssetStatus, archived *bool) ([]domain.Asset, error)
	GetForOwner(ownerID, id string) (*domain.Asset, error)
	CreateForOwner(ownerID string, asset *domain.Asset) error
	UpdateForOwner(ownerID string, asset *domain.Asset) error
	UpdateStatusForOwner(ownerID string, asset *domain.Asset) error
	UpdateArchiveForOwner(ownerID string, asset *domain.Asset) error
}
