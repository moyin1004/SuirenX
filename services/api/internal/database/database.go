package database

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func Open(path string) (*gorm.DB, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, fmt.Errorf("create database directory: %w", err)
	}

	db, err := gorm.Open(sqlite.Open(path), &gorm.Config{})
	if err != nil {
		return nil, fmt.Errorf("connect sqlite: %w", err)
	}

	if err := db.AutoMigrate(&repository.AssetRecord{}); err != nil {
		return nil, fmt.Errorf("migrate database: %w", err)
	}
	return db, nil
}
