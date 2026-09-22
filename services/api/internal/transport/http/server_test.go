package http

import (
	"encoding/json"
	"github.com/cloudwego/hertz/pkg/common/ut"
	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
	"path/filepath"
	"strings"
	"testing"
)

const testJWTSecret = "test-secret-with-at-least-32-bytes-long"

func request(s *Server, method, path, body string) *ut.ResponseRecorder {
	return ut.PerformRequest(s.h.Engine, method, path,
		&ut.Body{Body: strings.NewReader(body), Len: len(body)},
		ut.Header{Key: "Content-Type", Value: "application/json"})
}

func requestBearer(s *Server, method, path, body, token string) *ut.ResponseRecorder {
	return ut.PerformRequest(s.h.Engine, method, path,
		&ut.Body{Body: strings.NewReader(body), Len: len(body)},
		ut.Header{Key: "Content-Type", Value: "application/json"},
		ut.Header{Key: "Authorization", Value: "Bearer " + token})
}

func TestM5AccountIsolationVersionedSyncAndIdempotency(t *testing.T) {
	db, err := database.Open(filepath.Join(t.TempDir(), "assets.db"))
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = sqlDB.Close() })
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))

	for _, password := range []string{"short", strings.Repeat("a", 73), strings.Repeat("中", 25)} {
		body, _ := json.Marshal(map[string]string{"username": "invalid-password", "password": password})
		response := request(s, "POST", "/api/v1/auth/register", string(body))
		if response.Code != 400 {
			t.Fatalf("invalid registration should be 400, got %d", response.Code)
		}
	}

	register := func(username string) string {
		response := request(s, "POST", "/api/v1/auth/register", `{"username":"`+username+`","password":"correct horse battery staple"}`)
		if response.Code != 201 {
			t.Fatalf("register %s: %d %s", username, response.Code, response.Body)
		}
		var payload struct {
			AccessToken string `json:"access_token"`
		}
		if err := json.Unmarshal(response.Body.Bytes(), &payload); err != nil || payload.AccessToken == "" {
			t.Fatalf("register token: %s", response.Body)
		}
		return payload.AccessToken
	}
	alice, bob := register("alice"), register("bob")
	for _, method := range []string{"GET", "POST", "PUT", "DELETE"} {
		if response := requestBearer(s, method, "/api/v1/assets", `{}`, alice); response.Code != 404 {
			t.Fatalf("removed CRUD endpoint %s returned %d", method, response.Code)
		}
	}
	if response := request(s, "POST", "/api/v1/sync/assets", `{}`); response.Code != 401 {
		t.Fatalf("unauthenticated sync returned %d", response.Code)
	}
	assetID := "cross-device-asset"
	create := `{"cursor":0,"idempotency_key":"create-1","changes":[{"id":"` + assetID + `","base_version":0,"name":"Keyboard","price_cents":10000,"purchase_date":"2020-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"devices","purchase_channel":"","warranty_end_date":"","notes":"","tags":[]}]}`
	first := requestBearer(s, "POST", "/api/v1/sync/assets", create, alice)
	if first.Code != 200 || !strings.Contains(first.Body.String(), `"version":1`) {
		t.Fatalf("create sync: %d %s", first.Code, first.Body)
	}
	retry := requestBearer(s, "POST", "/api/v1/sync/assets", create, alice)
	if retry.Code != 200 || retry.Body.String() != first.Body.String() {
		t.Fatalf("idempotent retry differs: %d %s vs %s", retry.Code, retry.Body, first.Body)
	}

	bobPull := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"bob-pull","changes":[]}`, bob)
	if bobPull.Code != 200 || strings.Contains(bobPull.Body.String(), "Keyboard") || strings.Contains(bobPull.Body.String(), assetID) {
		t.Fatalf("owner isolation leaked: %d %s", bobPull.Code, bobPull.Body)
	}
	alicePull := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"alice-pull","changes":[]}`, alice)
	if alicePull.Code != 200 || !strings.Contains(alicePull.Body.String(), "Keyboard") {
		t.Fatalf("owner pull missing asset: %d %s", alicePull.Code, alicePull.Body)
	}

	expiry := `{"cursor":0,"expiry_cursor":0,"idempotency_key":"expiry-1","changes":[],"expiry_changes":[{"id":"expiry-1","base_version":0,"name":"Milk","category":"Food","package_expiry_date":"2030-01-01","opened_date":"","opened_validity_days":0,"location":"","notes":"","status":"IN_USE","archived_at":""}]}`
	expiryResponse := requestBearer(s, "POST", "/api/v1/sync/assets", expiry, alice)
	if expiryResponse.Code != 200 || !strings.Contains(expiryResponse.Body.String(), `"applied_expiry"`) || !strings.Contains(expiryResponse.Body.String(), `"next_expiry_cursor":1`) {
		t.Fatalf("expiry sync: %d %s", expiryResponse.Code, expiryResponse.Body)
	}
	bobExpiryPull := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"expiry_cursor":0,"idempotency_key":"bob-expiry-pull","changes":[]}`, bob)
	if bobExpiryPull.Code != 200 || strings.Contains(bobExpiryPull.Body.String(), "Milk") {
		t.Fatalf("expiry owner isolation leaked: %d %s", bobExpiryPull.Code, bobExpiryPull.Body)
	}

	conflict := requestBearer(s, "POST", "/api/v1/sync/assets", strings.Replace(create, "create-1", "conflict-1", 1), alice)
	if conflict.Code != 200 || !strings.Contains(conflict.Body.String(), `"conflicts"`) || !strings.Contains(conflict.Body.String(), `"remote_version":1`) {
		t.Fatalf("conflict not reported: %d %s", conflict.Code, conflict.Body)
	}
	update := strings.Replace(strings.Replace(create, "create-1", "update-1", 1), `"base_version":0`, `"base_version":1`, 1)
	update = strings.Replace(update, `"Keyboard"`, `"Mechanical Keyboard"`, 1)
	updated := requestBearer(s, "POST", "/api/v1/sync/assets", update, alice)
	if updated.Code != 200 || !strings.Contains(updated.Body.String(), `"version":2`) {
		t.Fatalf("versioned update: %d %s", updated.Code, updated.Body)
	}

	deleted := strings.Replace(strings.Replace(create, "create-1", "delete-1", 1), `"base_version":0`, `"base_version":2`, 1)
	deleted = strings.Replace(deleted, `"changes":[{`, `"changes":[{"deleted":true,`, 1)
	deletedResponse := requestBearer(s, "POST", "/api/v1/sync/assets", deleted, alice)
	if deletedResponse.Code != 200 || !strings.Contains(deletedResponse.Body.String(), `"deleted_at":"`) {
		t.Fatalf("tombstone: %d %s", deletedResponse.Code, deletedResponse.Body)
	}

	logout := requestBearer(s, "POST", "/api/v1/auth/logout", `{}`, alice)
	if logout.Code != 200 {
		t.Fatalf("logout: %d %s", logout.Code, logout.Body)
	}
	afterLogout := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"after-logout","changes":[]}`, alice)
	if afterLogout.Code != 200 {
		t.Fatalf("stateless jwt after logout: %d %s", afterLogout.Code, afterLogout.Body)
	}
}
