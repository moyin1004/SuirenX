package database

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"testing/fstest"
)

func bundled(t *testing.T) []migration {
	t.Helper()
	m, err := loadMigrations(migrationFiles)
	if err != nil {
		t.Fatal(err)
	}
	return m
}

func openRaw(t *testing.T, path string) *sql.DB {
	t.Helper()
	db, err := sql.Open("sqlite3", path)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return db
}

func assertScalar(t *testing.T, db *sql.DB, query string, want int) {
	t.Helper()
	var got int
	if err := db.QueryRow(query).Scan(&got); err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("%s = %d, want %d", query, got, want)
	}
}

func TestFreshDatabaseAppliesBaseline(t *testing.T) {
	migrations := bundled(t)
	if len(migrations) == 0 || migrations[0].version != 1 {
		t.Fatalf("baseline migration set: %+v", migrations)
	}
	path := filepath.Join(t.TempDir(), "assets.db")
	raw := openRaw(t, path)

	gormDB, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	conn, err := gormDB.DB()
	if err != nil {
		t.Fatal(err)
	}
	conn.Close()

	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(migrations))
	assertScalar(t, raw, "SELECT count(*) FROM pragma_table_info('asset_records')", 18)
	// The TEXT primary key also gets an implicit sqlite_autoindex; assert the
	// two explicit indexes by name.
	assertScalar(t, raw, "SELECT count(*) FROM pragma_index_list('asset_records') WHERE name='idx_asset_records_status'", 1)
	assertScalar(t, raw, "SELECT count(*) FROM pragma_index_list('asset_records') WHERE name='idx_asset_records_archived_at'", 1)

	if _, err := raw.Exec(`INSERT INTO asset_records(id,name,price_cents,purchase_date,status,retired_at,image_url,archived_at,icon_key) VALUES ('old','Existing',9007199254740993,'2020-01-01','RETIRED','2020-01-03','old-image','2026-09-01 12:00:00','camera')`); err != nil {
		t.Fatal(err)
	}
	var name, status, retired, image, icon string
	var cents int64
	var archived sql.NullString
	err = raw.QueryRow(`SELECT name,price_cents,status,retired_at,image_url,archived_at,icon_key FROM asset_records WHERE id='old'`).Scan(&name, &cents, &status, &retired, &image, &archived, &icon)
	if err != nil {
		t.Fatal(err)
	}
	if name != "Existing" || cents != 9007199254740993 || status != "RETIRED" || !strings.HasPrefix(retired, "2020-01-03") || image != "old-image" {
		t.Fatal("baseline data round-trip failed")
	}
	if !archived.Valid || !strings.HasPrefix(archived.String, "2026-09-01") || icon != "camera" {
		t.Fatalf("optional columns missing: %v %q", archived, icon)
	}

	var before string
	if err = raw.QueryRow("SELECT group_concat(applied_at) FROM schema_migrations").Scan(&before); err != nil {
		t.Fatal(err)
	}
	again, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	againConn, _ := again.DB()
	againConn.Close()
	var after string
	if err = raw.QueryRow("SELECT group_concat(applied_at) FROM schema_migrations").Scan(&after); err != nil {
		t.Fatal(err)
	}
	if before != after {
		t.Fatal("repeat open rewrote migration history")
	}
}

func TestPreBaselineSchemaIsRejectedWithoutChanges(t *testing.T) {
	path := filepath.Join(t.TempDir(), "assets.db")
	raw := openRaw(t, path)
	// A database with the table but no migration history predates the baseline.
	if _, err := raw.Exec(`CREATE TABLE asset_records (id TEXT PRIMARY KEY, name TEXT NOT NULL);`); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(path); err == nil {
		t.Fatal("adopted a pre-baseline schema")
	}
	// The failed batch rolls back together: even the history table creation
	// is undone, and the pre-existing table keeps its original shape.
	assertScalar(t, raw, "SELECT count(*) FROM sqlite_master WHERE name='schema_migrations'", 0)
	assertScalar(t, raw, "SELECT count(*) FROM pragma_table_info('asset_records')", 2)
	if _, err := raw.Exec(`INSERT INTO asset_records(id,name) VALUES ('x','X')`); err != nil {
		t.Fatalf("pre-baseline data was modified: %v", err)
	}
}

func TestUpgradeAppliesPendingMigrations(t *testing.T) {
	path := filepath.Join(t.TempDir(), "assets.db")
	raw := openRaw(t, path)
	base := bundled(t)
	if err := migrate(context.Background(), raw, base); err != nil {
		t.Fatal(err)
	}
	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(base))
	nextVersion := base[len(base)-1].version + 1
	next := append(append([]migration{}, base...), testMigration(nextVersion, `CREATE TABLE extra_notes(id INTEGER);`))
	if err := migrate(context.Background(), raw, next); err != nil {
		t.Fatal(err)
	}
	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(base)+1)
	assertScalar(t, raw, "SELECT count(*) FROM sqlite_master WHERE name='extra_notes'", 1)
}

func testMigration(version int, body string) migration {
	return migration{version: version, name: fmt.Sprintf("%03d_test.sql", version), body: body, checksum: fmt.Sprintf("%x", sha256.Sum256([]byte(body)))}
}

func TestFailedMigrationRollsBackDDLDataAndHistory(t *testing.T) {
	raw := openRaw(t, filepath.Join(t.TempDir(), "assets.db"))
	base := bundled(t)
	if err := migrate(context.Background(), raw, base); err != nil {
		t.Fatal(err)
	}
	if _, err := raw.Exec(`INSERT INTO asset_records(id,name,price_cents,status) VALUES ('a','A',123,'ACTIVE')`); err != nil {
		t.Fatal(err)
	}
	nextVersion := base[len(base)-1].version + 1
	failed := append(append([]migration{}, base...), testMigration(nextVersion, `CREATE TABLE partial_write(id INTEGER); UPDATE asset_records SET price_cents=0; INSERT INTO missing_table VALUES(1);`))
	err := migrate(context.Background(), raw, failed)
	if err == nil || !strings.Contains(err.Error(), fmt.Sprintf("%03d_test.sql", nextVersion)) {
		t.Fatalf("expected migration context: %v", err)
	}
	assertScalar(t, raw, "SELECT count(*) FROM sqlite_master WHERE name='partial_write'", 0)
	assertScalar(t, raw, "SELECT price_cents FROM asset_records", 123)
	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(base))
	fixed := append(append([]migration{}, base...), testMigration(nextVersion, `CREATE TABLE partial_write(id INTEGER);`))
	if err = migrate(context.Background(), raw, fixed); err != nil {
		t.Fatal(err)
	}
	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(base)+1)
}

func TestFreshFailureLeavesNoPartialSchema(t *testing.T) {
	raw := openRaw(t, filepath.Join(t.TempDir(), "assets.db"))
	base := bundled(t)
	nextVersion := base[len(base)-1].version + 1
	migrations := append(base, testMigration(nextVersion, "INVALID SQL"))
	if err := migrate(context.Background(), raw, migrations); err == nil {
		t.Fatal("expected failure")
	}
	assertScalar(t, raw, "SELECT count(*) FROM sqlite_master WHERE type='table'", 0)
	if err := migrate(context.Background(), raw, bundled(t)); err != nil {
		t.Fatal(err)
	}
}

func TestRejectIncompatibleHistory(t *testing.T) {
	for _, change := range []string{
		"UPDATE schema_migrations SET checksum='changed' WHERE version=1",
		"UPDATE schema_migrations SET name='renamed.sql' WHERE version=1",
		"INSERT INTO schema_migrations VALUES(4,'future.sql','unknown','2026-09-05T00:00:00Z')",
	} {
		t.Run(change, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "assets.db")
			raw := openRaw(t, path)
			if err := migrate(context.Background(), raw, bundled(t)); err != nil {
				t.Fatal(err)
			}
			if _, err := raw.Exec(change); err != nil {
				t.Fatal(err)
			}
			if _, err := Open(path); err == nil {
				t.Fatal("accepted incompatible history")
			}
		})
	}
}

func TestConcurrentStartupAppliesOnce(t *testing.T) {
	path := filepath.Join(t.TempDir(), "assets.db")
	const workers = 6
	start := make(chan struct{})
	results := make(chan error, workers)
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			db, err := Open(path)
			if err == nil {
				conn, _ := db.DB()
				err = conn.Close()
			}
			results <- err
		}()
	}
	close(start)
	wg.Wait()
	close(results)
	for err := range results {
		if err != nil {
			t.Fatal(err)
		}
	}
	raw := openRaw(t, path)
	assertScalar(t, raw, "SELECT count(*) FROM schema_migrations", len(bundled(t)))
}

func TestRejectInvalidMigrationFiles(t *testing.T) {
	for _, files := range []fstest.MapFS{
		{},
		{"migrations/bad.sql": {Data: []byte("SELECT 1;")}},
		{"migrations/002_skipped.sql": {Data: []byte("SELECT 1;")}},
		{"migrations/001_empty.sql": {Data: []byte(" ")}},
		// Same numeric version under different padding is a duplicate, not 001
		// followed by a new version.
		{
			"migrations/001_first.sql": {Data: []byte("SELECT 1;")},
			"migrations/1_second.sql":  {Data: []byte("SELECT 2;")},
		},
	} {
		if _, err := loadMigrations(files); err == nil {
			t.Fatal("accepted invalid migration files")
		}
	}
}

func TestLoadMigrationsAcceptsMultiDigitVersions(t *testing.T) {
	// Unpadded names glob in lexical order (1, 10, 2, ...); the loader must
	// order by numeric version so 999 -> 1000 keeps working.
	files := fstest.MapFS{}
	for i := 1; i <= 10; i++ {
		files[fmt.Sprintf("migrations/%d_step.sql", i)] = &fstest.MapFile{Data: []byte(fmt.Sprintf("SELECT %d;", i))}
	}
	got, err := loadMigrations(files)
	if err != nil {
		t.Fatal(err)
	}
	for i, m := range got {
		if m.version != i+1 {
			t.Fatalf("position %d loaded version %d; numeric ordering broken", i+1, m.version)
		}
	}
}

func TestDevelopmentSchemaUsesOnly001(t *testing.T) {
	migrations := bundled(t)
	if len(migrations) != 1 || migrations[0].name != "001_init.sql" {
		t.Fatalf("pre-release schema must stay in 001_init.sql; update AGENTS.md after release before adding versions: %+v", migrations)
	}
}
