package repository

import (
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"gorm.io/gorm"
)

type AccountRecord struct {
	ID           string `gorm:"primaryKey"`
	Username     string `gorm:"uniqueIndex;not null"`
	PasswordHash []byte `gorm:"not null"`
	Role         string `gorm:"not null;default:USER"`
	DisabledAt   *time.Time
	CreatedAt    time.Time `gorm:"not null"`
}

func (AccountRecord) TableName() string { return "accounts" }

type GormAuthRepository struct{ db *gorm.DB }

func NewGormAuthRepository(db *gorm.DB) *GormAuthRepository { return &GormAuthRepository{db: db} }

func (r *GormAuthRepository) CreateAccount(account *auth.Account) error {
	err := r.db.Create(&AccountRecord{ID: account.ID, Username: account.Username, PasswordHash: account.PasswordHash, Role: account.Role, CreatedAt: account.CreatedAt}).Error
	return err
}

// CreateRegisteredAccount checks the persisted server setting and inserts the
// account under one SQLite write transaction so closing registration cannot
// race with a successful signup.
func (r *GormAuthRepository) CreateRegisteredAccount(account *auth.Account) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var setting ServerSettingRecord
		if err := tx.Where("setting_key = ?", "allow_account_registration").First(&setting).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			// The setting was introduced after the existing settings table. Missing
			// means the compatibility default: allow registration.
			setting.Value = "true"
		} else if err != nil {
			return err
		}
		if setting.Value != "true" && setting.Value != "false" {
			return errors.New("invalid account registration setting")
		}
		if setting.Value != "true" {
			return auth.ErrRegistrationDisabled
		}
		return tx.Create(&AccountRecord{ID: account.ID, Username: account.Username, PasswordHash: account.PasswordHash, Role: account.Role, CreatedAt: account.CreatedAt}).Error
	})
}

func (r *GormAuthRepository) FindAccount(username string) (*auth.Account, error) {
	var record AccountRecord
	if err := r.db.Where("username = ?", username).First(&record).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return accountFromRecord(record), nil
}

func (r *GormAuthRepository) FindAccountByID(id string) (*auth.Account, error) {
	var record AccountRecord
	if err := r.db.Where("id = ?", id).First(&record).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return accountFromRecord(record), nil
}

func (r *GormAuthRepository) UpdateSuperadminPassword(username string, passwordHash []byte, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var account AccountRecord
		if err := tx.Where("username = ? AND role = ?", username, auth.RoleSuperadmin).First(&account).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return auth.ErrSuperadminNotFound
		} else if err != nil {
			return err
		}
		if err := tx.Model(&AccountRecord{}).Where("id = ?", account.ID).Update("password_hash", passwordHash).Error; err != nil {
			return err
		}
		if err := appendAdminAudit(tx, account.ID, "account", account.ID, "SUPERADMIN_PASSWORD_ROTATED", now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}

func accountFromRecord(record AccountRecord) *auth.Account {
	return &auth.Account{ID: record.ID, Username: record.Username, PasswordHash: record.PasswordHash, CreatedAt: record.CreatedAt, Role: record.Role, DisabledAt: record.DisabledAt}
}
