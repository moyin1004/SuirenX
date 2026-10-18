package http

import (
	"encoding/json"
	"path/filepath"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

func TestSalePriceSyncRoundTripLegacyOmissionAndClear(t *testing.T) {
	db, err := database.Open(filepath.Join(t.TempDir(), "sale.db"))
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, _ := db.DB()
	defer sqlDB.Close()
	server := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	health := request(server, "GET", "/healthz", "")
	var capability map[string]any
	if err := json.Unmarshal(health.Body.Bytes(), &capability); err != nil || capability["asset_sale_price"] != true {
		t.Fatalf("health %s", health.Body)
	}
	registration := request(server, "POST", "/api/v1/auth/register", `{"username":"sale-test","password":"printable password"}`)
	var token struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(registration.Body.Bytes(), &token); err != nil || token.AccessToken == "" {
		t.Fatalf("register: %s", registration.Body)
	}
	input := map[string]any{"id": "sale-asset", "base_version": 0, "name": "设备", "price_cents": 123456, "purchase_date": "2020-01-01", "status": "ACTIVE", "archived_at": "2026-10-01T00:00:00Z", "tags": []string{}}
	sync := func(key string, expected *int64) domain.SyncChange {
		t.Helper()
		body, _ := json.Marshal(map[string]any{"idempotency_key": key, "changes": []any{input}})
		response := requestBearer(server, "POST", "/api/v1/sync/assets", string(body), token.AccessToken)
		if response.Code != 200 {
			t.Fatalf("%s: %d %s", key, response.Code, response.Body)
		}
		var result domain.SyncBatchResult
		if err := json.Unmarshal(response.Body.Bytes(), &result); err != nil || len(result.Applied) != 1 {
			t.Fatalf("%s: %s", key, response.Body)
		}
		got := result.Applied[0].Asset.SalePriceCents
		if (got == nil) != (expected == nil) || got != nil && *got != *expected {
			t.Fatalf("%s sale got %v expected %v", key, got, expected)
		}
		input["base_version"] = result.Applied[0].Version
		return result.Applied[0]
	}
	sync("create", nil)
	cents := int64(80050)
	input["sale_price_cents"] = cents
	sync("sale", &cents)
	delete(input, "sale_price_cents") // pre-feature clients must not erase a price.
	input["archived_at"] = ""
	sync("legacy-restore", &cents)
	input["archived_at"] = "2026-10-01T00:00:00Z"
	input["sale_price_cents"] = 0
	zero := int64(0)
	sync("zero", &zero)
	input["sale_price_cents"] = nil
	sync("clear", nil)
	input["sale_price_cents"] = -1
	body, _ := json.Marshal(map[string]any{"idempotency_key": "negative", "changes": []any{input}})
	response := requestBearer(server, "POST", "/api/v1/sync/assets", string(body), token.AccessToken)
	if response.Code != 400 {
		t.Fatalf("negative accepted %d %s", response.Code, response.Body)
	}
	pull := requestBearer(server, "POST", "/api/v1/sync/assets", `{"idempotency_key":"pull","changes":[]}`, token.AccessToken)
	var history domain.SyncBatchResult
	if err := json.Unmarshal(pull.Body.Bytes(), &history); err != nil || len(history.Changes) != 5 {
		t.Fatalf("pull %s", pull.Body)
	}
	if history.Changes[1].Asset.SalePriceCents == nil || *history.Changes[1].Asset.SalePriceCents != cents {
		t.Fatal("sale missing in history")
	}
	if history.Changes[3].Asset.SalePriceCents == nil || *history.Changes[3].Asset.SalePriceCents != 0 {
		t.Fatal("zero lost")
	}
}
