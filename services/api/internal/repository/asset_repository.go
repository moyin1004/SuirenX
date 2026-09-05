package repository

import "github.com/moyin1004/suirenx/services/api/internal/domain"

type AssetRepository interface {
	List(status *domain.AssetStatus) ([]domain.Asset, error)
	Create(asset *domain.Asset) error
	Count() (int64, error)
}
