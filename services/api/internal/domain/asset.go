package domain

import "time"

type AssetStatus string

const (
	AssetStatusActive  AssetStatus = "ACTIVE"
	AssetStatusRetired AssetStatus = "RETIRED"
)

func (s AssetStatus) Valid() bool {
	return s == AssetStatusActive || s == AssetStatusRetired
}

type Asset struct {
	ID           string
	Name         string
	PriceCents   int64
	PurchaseDate time.Time
	RetiredAt    *time.Time
	ArchivedAt   *time.Time
	Status       AssetStatus
	ImageURL     string
	IconKey      string
	CreatedAt    time.Time
	UpdatedAt    time.Time
}
