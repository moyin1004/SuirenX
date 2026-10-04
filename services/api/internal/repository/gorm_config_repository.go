package repository

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"gorm.io/gorm"
)

var (
	ErrConfigNotFound     = errors.New("configuration not found")
	ErrConfigNameTaken    = errors.New("configuration name is already in use")
	ErrConfigTooLarge     = errors.New("configuration exceeds the configured size limit")
	ErrApiTokenInvalid    = errors.New("invalid API token")
	ErrApiTokenNotFound   = errors.New("API token not found")
	ErrApiTokenExpired    = errors.New("API token expired")
	ErrApiTokenRevoked    = errors.New("API token revoked")
	ErrApiTokenScope      = errors.New("API token is not authorized for this configuration")
	ErrInvalidConfig      = errors.New("invalid configuration file")
	ErrInvalidApiToken    = errors.New("invalid API token settings")
	ErrInvalidConfigLimit = errors.New("invalid configuration size limit")
)

type ConfigFileRecord struct {
	Key         string `gorm:"primaryKey;column:config_key"`
	DisplayName string `gorm:"not null"`
	Content     string `gorm:"not null"`
	CreatedBy   string `gorm:"not null"`
	UpdatedBy   string `gorm:"not null"`
	CreatedAt   time.Time
	UpdatedAt   time.Time
	DeletedAt   *time.Time
}

func (ConfigFileRecord) TableName() string { return "config_files" }

type ApiTokenRecord struct {
	ID            string `gorm:"primaryKey"`
	TokenHash     string `gorm:"not null;uniqueIndex"`
	Label         string `gorm:"not null"`
	TransportMode string `gorm:"not null"`
	CreatedBy     string `gorm:"not null"`
	CreatedAt     time.Time
	ExpiresAt     *time.Time
	RevokedAt     *time.Time
	LastUsedAt    *time.Time
}

func (ApiTokenRecord) TableName() string { return "api_tokens" }

type ApiTokenScopeRecord struct {
	TokenID   string `gorm:"primaryKey"`
	ConfigKey string `gorm:"primaryKey"`
}

func (ApiTokenScopeRecord) TableName() string { return "api_token_config_scopes" }

type ServerSettingRecord struct {
	Key       string `gorm:"primaryKey;column:setting_key"`
	Value     string `gorm:"column:setting_value;not null"`
	UpdatedBy string `gorm:"not null"`
	UpdatedAt time.Time
}

func (ServerSettingRecord) TableName() string { return "server_settings" }

func (r *GormAuthRepository) MaxConfigBytes() (int64, error) {
	var record ServerSettingRecord
	if err := r.db.Where("setting_key = ?", "config_max_bytes").First(&record).Error; err != nil {
		return 0, err
	}
	var limit int64
	if _, err := fmt.Sscan(record.Value, &limit); err != nil || limit < 1 {
		return 0, ErrInvalidConfigLimit
	}
	return limit, nil
}

func (r *GormAuthRepository) SetMaxConfigBytes(limit int64, actorID string, now time.Time) error {
	if limit < 1 || limit > 100*1024*1024 || actorID == "" {
		return ErrInvalidConfigLimit
	}
	return r.db.Transaction(func(tx *gorm.DB) error {
		setting := ServerSettingRecord{Key: "config_max_bytes", Value: fmt.Sprint(limit), UpdatedBy: actorID, UpdatedAt: now}
		if err := tx.Save(&setting).Error; err != nil {
			return err
		}
		if err := appendAdminAudit(tx, actorID, "server_setting", "config_max_bytes", "CONFIG_SIZE_LIMIT_UPDATED", now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) AccountRegistrationEnabled() (bool, error) {
	var record ServerSettingRecord
	if err := r.db.Where("setting_key = ?", "allow_account_registration").First(&record).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return true, nil
		}
		return false, err
	}
	switch record.Value {
	case "true":
		return true, nil
	case "false":
		return false, nil
	default:
		return false, errors.New("invalid account registration setting")
	}
}

func (r *GormAuthRepository) SetAccountRegistrationEnabled(enabled bool, actorID string, now time.Time) error {
	if actorID == "" {
		return errors.New("superadmin actor is required")
	}
	value, action := "false", "ACCOUNT_REGISTRATION_DISABLED"
	if enabled {
		value, action = "true", "ACCOUNT_REGISTRATION_ENABLED"
	}
	return r.db.Transaction(func(tx *gorm.DB) error {
		setting := ServerSettingRecord{Key: "allow_account_registration", Value: value, UpdatedBy: actorID, UpdatedAt: now}
		if err := tx.Save(&setting).Error; err != nil {
			return err
		}
		if err := appendAdminAudit(tx, actorID, "server_setting", "allow_account_registration", action, now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) ListConfigs() ([]domain.ConfigFile, error) {
	var records []ConfigFileRecord
	if err := r.db.Select("config_key", "display_name", "created_at", "updated_at", "deleted_at").Where("deleted_at IS NULL").Order("updated_at DESC, config_key ASC").Find(&records).Error; err != nil {
		return nil, err
	}
	configs := make([]domain.ConfigFile, 0, len(records))
	for _, record := range records {
		configs = append(configs, configFromRecord(record))
	}
	return configs, nil
}

func (r *GormAuthRepository) GetConfig(key string) (*domain.ConfigFile, error) {
	var record ConfigFileRecord
	if err := r.db.Where("config_key = ? AND deleted_at IS NULL", key).First(&record).Error; errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, ErrConfigNotFound
	} else if err != nil {
		return nil, err
	}
	config := configFromRecord(record)
	return &config, nil
}

func (r *GormAuthRepository) CreateConfig(config *domain.ConfigFile, actorID string) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		limit, err := maxConfigBytes(tx)
		if err != nil {
			return err
		}
		if int64(len([]byte(config.Content))) > limit {
			return ErrConfigTooLarge
		}
		record := ConfigFileRecord{Key: config.Key, DisplayName: config.DisplayName, Content: config.Content, CreatedBy: actorID, UpdatedBy: actorID, CreatedAt: config.CreatedAt, UpdatedAt: config.UpdatedAt}
		if err := tx.Create(&record).Error; err != nil {
			if isUniqueViolation(err) {
				return ErrConfigNameTaken
			}
			return err
		}
		if err := appendAdminAudit(tx, actorID, "config", config.Key, "CONFIG_CREATED", config.CreatedAt); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) UpdateConfig(key, displayName, content, actorID string, now time.Time) (*domain.ConfigFile, error) {
	var result ConfigFileRecord
	err := r.db.Transaction(func(tx *gorm.DB) error {
		limit, err := maxConfigBytes(tx)
		if err != nil {
			return err
		}
		if int64(len([]byte(content))) > limit {
			return ErrConfigTooLarge
		}
		var current ConfigFileRecord
		if err := tx.Where("config_key = ? AND deleted_at IS NULL", key).First(&current).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrConfigNotFound
		} else if err != nil {
			return err
		}
		current.DisplayName, current.Content, current.UpdatedBy, current.UpdatedAt = displayName, content, actorID, now
		if err := tx.Save(&current).Error; err != nil {
			if isUniqueViolation(err) {
				return ErrConfigNameTaken
			}
			return err
		}
		if err := appendAdminAudit(tx, actorID, "config", key, "CONFIG_UPDATED", now); err != nil {
			return err
		}
		if err := pruneAdminAudit(tx); err != nil {
			return err
		}
		result = current
		return nil
	})
	if err != nil {
		return nil, err
	}
	config := configFromRecord(result)
	return &config, nil
}

func (r *GormAuthRepository) DeleteConfig(key, actorID string, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		result := tx.Model(&ConfigFileRecord{}).Where("config_key = ? AND deleted_at IS NULL", key).Updates(map[string]any{"deleted_at": now, "updated_by": actorID, "updated_at": now})
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected == 0 {
			return ErrConfigNotFound
		}
		if err := appendAdminAudit(tx, actorID, "config", key, "CONFIG_DELETED", now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) ListApiTokens() ([]domain.ApiToken, error) {
	var records []ApiTokenRecord
	if err := r.db.Order("created_at DESC, id ASC").Find(&records).Error; err != nil {
		return nil, err
	}
	tokens := make([]domain.ApiToken, 0, len(records))
	for _, record := range records {
		var scopes []ApiTokenScopeRecord
		if err := r.db.Where("token_id = ?", record.ID).Order("config_key ASC").Find(&scopes).Error; err != nil {
			return nil, err
		}
		keys := make([]string, 0, len(scopes))
		for _, scope := range scopes {
			keys = append(keys, scope.ConfigKey)
		}
		tokens = append(tokens, domain.ApiToken{ID: record.ID, Label: record.Label, TransportMode: record.TransportMode, ConfigKeys: keys, CreatedAt: record.CreatedAt, ExpiresAt: record.ExpiresAt, RevokedAt: record.RevokedAt, LastUsedAt: record.LastUsedAt})
	}
	return tokens, nil
}

func (r *GormAuthRepository) CreateApiToken(token *domain.ApiToken, tokenHash, actorID string) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var activeCount int64
		if err := tx.Model(&ConfigFileRecord{}).Where("deleted_at IS NULL AND config_key IN ?", token.ConfigKeys).Count(&activeCount).Error; err != nil {
			return err
		}
		if activeCount != int64(len(token.ConfigKeys)) {
			return ErrConfigNotFound
		}
		record := ApiTokenRecord{ID: token.ID, TokenHash: tokenHash, Label: token.Label, TransportMode: token.TransportMode, CreatedBy: actorID, CreatedAt: token.CreatedAt, ExpiresAt: token.ExpiresAt}
		if err := tx.Create(&record).Error; err != nil {
			return err
		}
		for _, key := range token.ConfigKeys {
			if err := tx.Create(&ApiTokenScopeRecord{TokenID: token.ID, ConfigKey: key}).Error; err != nil {
				return err
			}
		}
		if err := appendAdminAudit(tx, actorID, "api_token", token.ID, "API_TOKEN_CREATED", token.CreatedAt); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) RevokeApiToken(id, actorID string, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		result := tx.Model(&ApiTokenRecord{}).Where("id = ? AND revoked_at IS NULL", id).Update("revoked_at", now)
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected == 0 {
			var token ApiTokenRecord
			if err := tx.Where("id = ?", id).First(&token).Error; errors.Is(err, gorm.ErrRecordNotFound) {
				return ErrApiTokenInvalid
			} else if err != nil {
				return err
			}
		}
		if err := appendAdminAudit(tx, actorID, "api_token", id, "API_TOKEN_REVOKED", now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) UpdateApiToken(id, label string, configKeys []string, actorID string, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var token ApiTokenRecord
		if err := tx.Where("id = ?", id).First(&token).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrApiTokenNotFound
		} else if err != nil {
			return err
		}
		if token.RevokedAt != nil {
			return ErrApiTokenRevoked
		}
		var activeCount int64
		if err := tx.Model(&ConfigFileRecord{}).Where("deleted_at IS NULL AND config_key IN ?", configKeys).Count(&activeCount).Error; err != nil {
			return err
		}
		if activeCount != int64(len(configKeys)) {
			return ErrConfigNotFound
		}
		if err := tx.Model(&ApiTokenRecord{}).Where("id = ?", id).Update("label", label).Error; err != nil {
			return err
		}
		if err := tx.Where("token_id = ?", id).Delete(&ApiTokenScopeRecord{}).Error; err != nil {
			return err
		}
		for _, key := range configKeys {
			if err := tx.Create(&ApiTokenScopeRecord{TokenID: id, ConfigKey: key}).Error; err != nil {
				return err
			}
		}
		if err := appendAdminAudit(tx, actorID, "api_token", id, "API_TOKEN_UPDATED", now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func (r *GormAuthRepository) ReadConfigContent(key, tokenHash, suppliedMode string, now time.Time) (string, error) {
	var content string
	err := r.db.Transaction(func(tx *gorm.DB) error {
		var token ApiTokenRecord
		if err := tx.Where("token_hash = ?", tokenHash).First(&token).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrApiTokenInvalid
		} else if err != nil {
			return err
		}
		if token.TransportMode != suppliedMode {
			return ErrApiTokenInvalid
		}
		if token.RevokedAt != nil {
			return ErrApiTokenRevoked
		}
		if token.ExpiresAt != nil && !token.ExpiresAt.After(now) {
			return ErrApiTokenExpired
		}
		var scope ApiTokenScopeRecord
		if err := tx.Where("token_id = ? AND config_key = ?", token.ID, key).First(&scope).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrApiTokenScope
		} else if err != nil {
			return err
		}
		var config ConfigFileRecord
		if err := tx.Where("config_key = ? AND deleted_at IS NULL", key).First(&config).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrConfigNotFound
		} else if err != nil {
			return err
		}
		if err := tx.Model(&ApiTokenRecord{}).Where("id = ?", token.ID).Update("last_used_at", now).Error; err != nil {
			return err
		}
		content = config.Content
		return nil
	})
	return content, err
}

func configFromRecord(record ConfigFileRecord) domain.ConfigFile {
	return domain.ConfigFile{Key: record.Key, DisplayName: record.DisplayName, Content: record.Content, CreatedAt: record.CreatedAt, UpdatedAt: record.UpdatedAt, DeletedAt: record.DeletedAt}
}

func maxConfigBytes(db *gorm.DB) (int64, error) {
	var record ServerSettingRecord
	if err := db.Where("setting_key = ?", "config_max_bytes").First(&record).Error; err != nil {
		return 0, err
	}
	var limit int64
	if _, err := fmt.Sscan(record.Value, &limit); err != nil || limit < 1 {
		return 0, ErrInvalidConfigLimit
	}
	return limit, nil
}

func appendAdminAudit(tx *gorm.DB, actorID, targetType, targetID, action string, now time.Time) error {
	return tx.Create(&AdminAuditRecord{ActorID: actorID, TargetType: targetType, TargetID: targetID, Action: action, CreatedAt: now}).Error
}

func pruneAdminAudit(tx *gorm.DB) error {
	return tx.Exec(`DELETE FROM admin_audit_log WHERE id NOT IN (SELECT id FROM admin_audit_log ORDER BY id DESC LIMIT 100)`).Error
}

func isUniqueViolation(err error) bool {
	return err != nil && (strings.Contains(strings.ToLower(err.Error()), "unique constraint") || strings.Contains(strings.ToLower(err.Error()), "duplicate key"))
}
