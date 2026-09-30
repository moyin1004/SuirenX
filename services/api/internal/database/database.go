package database

import (
	"context"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func Open(path string) (*gorm.DB, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, fmt.Errorf("create database directory: %w", err)
	}

	absolutePath, err := filepath.Abs(path)
	if err != nil {
		return nil, fmt.Errorf("resolve database path: %w", err)
	}
	// Reserve the SQLite writer before reading versions. Deferred transactions
	// can deadlock while upgrading concurrent readers to writers, bypassing the
	// busy timeout and turning an ordinary sync conflict into an HTTP 500.
	dsn := url.URL{Scheme: "file", Path: absolutePath}
	options := url.Values{"_txlock": {"immediate"}, "_busy_timeout": {"5000"}}
	dsn.RawQuery = options.Encode()
	db, err := gorm.Open(sqlite.Open(dsn.String()), &gorm.Config{})
	if err != nil {
		return nil, fmt.Errorf("connect sqlite: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("get database connection: %w", err)
	}
	migrations, err := loadMigrations(migrationFiles)
	if err != nil {
		sqlDB.Close()
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := migrate(ctx, sqlDB, migrations); err != nil {
		sqlDB.Close()
		return nil, fmt.Errorf("migrate database: %w", err)
	}
	return db, nil
}
