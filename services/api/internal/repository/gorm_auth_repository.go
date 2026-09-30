package repository

import (
	"errors"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"gorm.io/gorm"
)

type AccountRecord struct {
	ID           string    `gorm:"primaryKey"`
	Username     string    `gorm:"uniqueIndex;not null"`
	PasswordHash []byte    `gorm:"not null"`
	CreatedAt    time.Time `gorm:"not null"`
}

func (AccountRecord) TableName() string { return "accounts" }

type GormAuthRepository struct{ db *gorm.DB }

func NewGormAuthRepository(db *gorm.DB) *GormAuthRepository { return &GormAuthRepository{db: db} }

func (r *GormAuthRepository) CreateAccount(account *auth.Account) error {
	err := r.db.Create(&AccountRecord{ID: account.ID, Username: account.Username, PasswordHash: account.PasswordHash, CreatedAt: account.CreatedAt}).Error
	return err
}

func (r *GormAuthRepository) FindAccount(username string) (*auth.Account, error) {
	var record AccountRecord
	if err := r.db.Where("username = ?", username).First(&record).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &auth.Account{ID: record.ID, Username: record.Username, PasswordHash: record.PasswordHash, CreatedAt: record.CreatedAt}, nil
}
