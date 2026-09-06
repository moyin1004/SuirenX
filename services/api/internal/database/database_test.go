package database

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

// The baseline schema must satisfy the GORM repository end to end: icon and
// archive columns persist across reopening, and restoring returns the asset
// to the current list.
func TestBaselineSchemaPersistsIconAndArchiveAcrossReopen(t *testing.T) {
	path := filepath.Join(t.TempDir(), "baseline.db")

	db, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	conn, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	raw := openRaw(t, path)
	if _, err := raw.Exec(`INSERT INTO asset_records(id,name,price_cents,purchase_date,status) VALUES ('legacy','Keyboard',10000,'2026-01-01 00:00:00','ACTIVE')`); err != nil {
		t.Fatal(err)
	}

	repo := repository.NewGormAssetRepository(db)
	asset, err := repo.Get("legacy")
	if err != nil || asset.Name != "Keyboard" || asset.PriceCents != 10000 || asset.ArchivedAt != nil {
		t.Fatalf("baseline read: %+v %v", asset, err)
	}
	asset.IconKey = "keyboard"
	if err = repo.Update(asset); err != nil {
		t.Fatal(err)
	}
	now := time.Now().UTC().Truncate(time.Second)
	asset.ArchivedAt = &now
	if err = repo.UpdateArchive(asset); err != nil {
		t.Fatal(err)
	}
	if err = conn.Close(); err != nil {
		t.Fatal(err)
	}

	reopened, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	reopenedConn, err := reopened.DB()
	if err != nil {
		t.Fatal(err)
	}
	defer reopenedConn.Close()
	repo = repository.NewGormAssetRepository(reopened)
	asset, err = repo.Get("legacy")
	if err != nil || asset.ArchivedAt == nil || !asset.ArchivedAt.Equal(now) || asset.IconKey != "keyboard" {
		t.Fatalf("reopen: %+v %v", asset, err)
	}
	asset.ArchivedAt = nil
	if err = repo.UpdateArchive(asset); err != nil {
		t.Fatal(err)
	}
	current := false
	assets, err := repo.List(nil, &current)
	if err != nil || len(assets) != 1 || assets[0].PriceCents != 10000 {
		t.Fatalf("restore: %+v %v", assets, err)
	}
}
