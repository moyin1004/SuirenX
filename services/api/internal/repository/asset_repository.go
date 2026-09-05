package repository

import (
	"errors"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
)

// ErrNotFound is returned when no asset matches the requested id.
var ErrNotFound = errors.New("asset not found")

type AssetRepository interface {
	List(status *domain.AssetStatus) ([]domain.Asset, error)
	Get(id string) (*domain.Asset, error)
	Create(asset *domain.Asset) error
	Count() (int64, error)
}
