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
	ID              string
	OwnerID         string
	Name            string
	PriceCents      int64
	PurchaseDate    time.Time
	RetiredAt       *time.Time
	ArchivedAt      *time.Time
	Status          AssetStatus
	ImageURL        string
	IconKey         string
	PurchaseChannel string
	WarrantyEndDate *time.Time
	Notes           string
	Tags            []string
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

type SyncAsset struct {
	ID              string   `json:"id"`
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

type SyncChange struct {
	Cursor    int64     `json:"cursor"`
	Version   int64     `json:"version"`
	DeletedAt string    `json:"deleted_at"`
	Asset     SyncAsset `json:"asset"`
}

type SyncConflict struct {
	ID            string    `json:"id"`
	BaseVersion   int64     `json:"base_version"`
	RemoteVersion int64     `json:"remote_version"`
	Remote        SyncAsset `json:"remote"`
}

type SyncBatchResult struct {
	NextCursor       int64                `json:"next_cursor"`
	NextExpiryCursor int64                `json:"next_expiry_cursor"`
	Applied          []SyncChange         `json:"applied"`
	Changes          []SyncChange         `json:"changes"`
	Conflicts        []SyncConflict       `json:"conflicts"`
	AppliedExpiry    []SyncExpiryChange   `json:"applied_expiry"`
	ExpiryChanges    []SyncExpiryChange   `json:"expiry_changes"`
	ExpiryConflicts  []SyncExpiryConflict `json:"expiry_conflicts"`
}

type SyncExpiryItem struct {
	ID                 string `json:"id"`
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

type SyncExpiryChange struct {
	Cursor    int64          `json:"cursor"`
	Version   int64          `json:"version"`
	DeletedAt string         `json:"deleted_at"`
	Item      SyncExpiryItem `json:"item"`
}

type SyncExpiryConflict struct {
	ID            string         `json:"id"`
	BaseVersion   int64          `json:"base_version"`
	RemoteVersion int64          `json:"remote_version"`
	Remote        SyncExpiryItem `json:"remote"`
}
