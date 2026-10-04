package repository

import (
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
)

// ErrNotFound is returned when no asset matches the requested id.
var ErrNotFound = errors.New("asset not found")
var ErrLastSuperadmin = errors.New("cannot disable the last active superadmin")
var ErrRecordArchived = errors.New("archived record must be restored before editing")

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

type OwnerAssetDeleteRepository interface {
	DeleteForOwner(ownerID, id string, now time.Time) error
}

type AdminRepository interface {
	ListAccounts() ([]domain.AdminAccount, error)
	SetAccountDisabled(accountID, actorID string, disabled bool, now time.Time) error
	RecordAudit(actorID, targetType, targetID, action string, now time.Time) error
}

type ConfigRepository interface {
	MaxConfigBytes() (int64, error)
	AccountRegistrationEnabled() (bool, error)
	SetAccountRegistrationEnabled(enabled bool, actorID string, now time.Time) error
	ListConfigs() ([]domain.ConfigFile, error)
	GetConfig(key string) (*domain.ConfigFile, error)
	CreateConfig(config *domain.ConfigFile, actorID string) error
	UpdateConfig(key, displayName, content, actorID string, now time.Time) (*domain.ConfigFile, error)
	DeleteConfig(key, actorID string, now time.Time) error
	SetMaxConfigBytes(limit int64, actorID string, now time.Time) error
	ListApiTokens() ([]domain.ApiToken, error)
	CreateApiToken(token *domain.ApiToken, tokenHash, actorID string) error
	UpdateApiToken(id, label string, configKeys []string, actorID string, now time.Time) error
	RevokeApiToken(id, actorID string, now time.Time) error
	ReadConfigContent(key, tokenHash, suppliedMode string, now time.Time) (string, error)
}

type ExpiryReadRepository interface {
	ListExpiryForOwner(ownerID string) ([]domain.VersionedExpiryItem, error)
}
