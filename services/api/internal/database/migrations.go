package database

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"embed"
	"fmt"
	"io/fs"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

//go:embed migrations/*.sql
var migrationFiles embed.FS

type migration struct {
	version  int
	name     string
	body     string
	checksum string
}

func loadMigrations(files fs.FS) ([]migration, error) {
	paths, err := fs.Glob(files, "migrations/*.sql")
	if err != nil {
		return nil, fmt.Errorf("list migration files: %w", err)
	}
	if len(paths) == 0 {
		return nil, fmt.Errorf("no migration files")
	}
	pattern := regexp.MustCompile(`^migrations/([0-9]+)_[a-z0-9_]+\.sql$`)
	result := make([]migration, 0, len(paths))
	for _, path := range paths {
		match := pattern.FindStringSubmatch(path)
		if match == nil {
			return nil, fmt.Errorf("invalid migration filename: %s", path)
		}
		version, _ := strconv.Atoi(match[1])
		body, err := fs.ReadFile(files, path)
		if err != nil {
			return nil, fmt.Errorf("read migration %s: %w", path, err)
		}
		if strings.TrimSpace(string(body)) == "" {
			return nil, fmt.Errorf("empty migration: %s", path)
		}
		result = append(result, migration{version, strings.TrimPrefix(path, "migrations/"), string(body), fmt.Sprintf("%x", sha256.Sum256(body))})
	}
	// File globbing is lexical: once numbers grow past a fixed width (e.g.
	// 999 -> 1000), lexical order diverges from numeric order. Order by the
	// parsed version before enforcing consecutiveness and duplicate detection.
	sort.Slice(result, func(i, j int) bool { return result[i].version < result[j].version })
	for i, m := range result {
		if m.version != i+1 {
			return nil, fmt.Errorf("migration versions must be consecutive from 001: %s", m.name)
		}
	}
	return result, nil
}

// All pending DDL and version records commit together. BEGIN IMMEDIATE obtains
// the SQLite write reservation before inspecting history, serializing concurrent
// starters. On any error the caller closes the database instead of serving it.
func migrate(ctx context.Context, db *sql.DB, migrations []migration) error {
	conn, err := db.Conn(ctx)
	if err != nil {
		return fmt.Errorf("acquire migration connection: %w", err)
	}
	defer conn.Close()
	if _, err = conn.ExecContext(ctx, "PRAGMA busy_timeout = 5000"); err != nil {
		return fmt.Errorf("set migration lock timeout: %w", err)
	}
	if _, err = conn.ExecContext(ctx, "BEGIN IMMEDIATE"); err != nil {
		return fmt.Errorf("lock database for migration: %w", err)
	}
	defer conn.ExecContext(context.Background(), "ROLLBACK")
	if _, err = conn.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  checksum TEXT NOT NULL,
  applied_at TEXT NOT NULL
 )`); err != nil {
		return fmt.Errorf("create migration history: %w", err)
	}
	count, err := validateHistory(ctx, conn, migrations)
	if err != nil {
		return err
	}
	// A database with no history but an existing asset_records table is a
	// pre-baseline schema. Applying the baseline fails on CREATE TABLE and the
	// whole batch rolls back, so startup aborts instead of silently serving a
	// mismatched schema.
	for _, m := range migrations[count:] {
		if _, err = conn.ExecContext(ctx, m.body); err != nil {
			return fmt.Errorf("apply migration %s: %w", m.name, err)
		}
		if err = recordMigration(ctx, conn, m); err != nil {
			return err
		}
	}
	if _, err = conn.ExecContext(ctx, "COMMIT"); err != nil {
		return fmt.Errorf("commit migrations: %w", err)
	}
	return nil
}

func validateHistory(ctx context.Context, conn *sql.Conn, migrations []migration) (int, error) {
	rows, err := conn.QueryContext(ctx, "SELECT version,name,checksum FROM schema_migrations ORDER BY version")
	if err != nil {
		return 0, fmt.Errorf("read migration history: %w", err)
	}
	defer rows.Close()
	count := 0
	for rows.Next() {
		var version int
		var name, checksum string
		if err = rows.Scan(&version, &name, &checksum); err != nil {
			return 0, fmt.Errorf("decode migration history: %w", err)
		}
		if version != count+1 || count >= len(migrations) {
			return 0, fmt.Errorf("unsupported migration history at version %d (binary supports %d); use a matching or newer binary", version, len(migrations))
		}
		expected := migrations[count]
		if name != expected.name || checksum != expected.checksum {
			return 0, fmt.Errorf("migration %d name/checksum mismatch; applied migrations must not be edited", version)
		}
		count++
	}
	if err = rows.Err(); err != nil {
		return 0, fmt.Errorf("iterate migration history: %w", err)
	}
	return count, nil
}

func recordMigration(ctx context.Context, conn *sql.Conn, m migration) error {
	_, err := conn.ExecContext(ctx, "INSERT INTO schema_migrations(version,name,checksum,applied_at) VALUES (?,?,?,?)", m.version, m.name, m.checksum, time.Now().UTC().Format(time.RFC3339))
	if err != nil {
		return fmt.Errorf("record migration %s: %w", m.name, err)
	}
	return nil
}
