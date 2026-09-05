package database

import (
	"path/filepath"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

// The baseline schema must satisfy the GORM repository end to end: a row
// inserted through SQL is readable through GORM and after reopening.
func TestBaselineSchemaPersistsAcrossReopen(t *testing.T) {
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
	if err != nil || asset.Name != "Keyboard" || asset.PriceCents != 10000 {
		t.Fatalf("baseline read: %+v %v", asset, err)
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
	if err != nil || asset.Name != "Keyboard" || asset.PriceCents != 10000 {
		t.Fatalf("reopen: %+v %v", asset, err)
	}
}
