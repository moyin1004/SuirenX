package repository

import (
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"gorm.io/gorm"
)

type AdminAuditRecord struct {
	ID         int64     `gorm:"primaryKey;autoIncrement"`
	ActorID    string    `gorm:"not null"`
	TargetType string    `gorm:"not null"`
	TargetID   string    `gorm:"not null"`
	Action     string    `gorm:"not null"`
	CreatedAt  time.Time `gorm:"not null"`
}

func (AdminAuditRecord) TableName() string { return "admin_audit_log" }

func (r *GormAuthRepository) ListAccounts() ([]domain.AdminAccount, error) {
	var records []AccountRecord
	if err := r.db.Order("created_at DESC, id ASC").Find(&records).Error; err != nil {
		return nil, err
	}
	accounts := make([]domain.AdminAccount, 0, len(records))
	for _, record := range records {
		accounts = append(accounts, domain.AdminAccount{ID: record.ID, Username: record.Username, Role: record.Role, CreatedAt: record.CreatedAt, DisabledAt: record.DisabledAt})
	}
	return accounts, nil
}

func (r *GormAuthRepository) SetAccountDisabled(accountID, actorID string, disabled bool, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var account AccountRecord
		if err := tx.Where("id = ?", accountID).First(&account).Error; errors.Is(err, gorm.ErrRecordNotFound) {
			return ErrNotFound
		} else if err != nil {
			return err
		}
		if account.Role == "SUPERADMIN" && disabled {
			var active int64
			if err := tx.Model(&AccountRecord{}).Where("role = ? AND disabled_at IS NULL", "SUPERADMIN").Count(&active).Error; err != nil {
				return err
			}
			if active <= 1 {
				return ErrLastSuperadmin
			}
		}
		var disabledAt *time.Time
		action := "ACCOUNT_ENABLED"
		if disabled {
			disabledAt, action = &now, "ACCOUNT_DISABLED"
		}
		if err := tx.Model(&AccountRecord{}).Where("id = ?", accountID).Update("disabled_at", disabledAt).Error; err != nil {
			return err
		}
		if err := tx.Create(&AdminAuditRecord{ActorID: actorID, TargetType: "account", TargetID: accountID, Action: action, CreatedAt: now}).Error; err != nil {
			return err
		}
		return tx.Exec(`DELETE FROM admin_audit_log WHERE id NOT IN (SELECT id FROM admin_audit_log ORDER BY id DESC LIMIT 100)`).Error
	})
}

func (r *GormAuthRepository) RecordAudit(actorID, targetType, targetID, action string, now time.Time) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		if err := appendAdminAudit(tx, actorID, targetType, targetID, action, now); err != nil {
			return err
		}
		return pruneAdminAudit(tx)
	})
}
