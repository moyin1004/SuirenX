package http

import (
	"encoding/json"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/common/ut"
	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

func testServer(t *testing.T) (*Server, func()) {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "assets.db"))
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	closeDB := func() { _ = sqlDB.Close() }
	t.Cleanup(closeDB)
	return NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db))), closeDB
}

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

func TestAssetJSONRoundTrip(t *testing.T) {
	s, _ := testServer(t)
	empty := request(s, "GET", "/api/v1/assets", "")
	if empty.Code != 200 || empty.Body.String() != `{"assets":[]}` {
		t.Fatalf("empty list: %d %s", empty.Code, empty.Body)
	}
	today := time.Now().Format(time.DateOnly)
	created := request(s, "POST", "/api/v1/assets", `{"name":" Keyboard ","price_cents":0,"purchase_date":"`+today+`","image_url":""}`)
	if created.Code != 201 {
		t.Fatalf("create: %d %s", created.Code, created.Body)
	}
	var response struct {
		Asset map[string]json.RawMessage `json:"asset"`
	}
	if err := json.Unmarshal(created.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	for key, want := range map[string]string{
		"name": `"Keyboard"`, "price_cents": "0", "purchase_date": `"` + today + `"`,
		"status": `"ACTIVE"`, "image_url": `""`, "held_days": "1", "daily_cost_cents": "0",
	} {
		if string(response.Asset[key]) != want {
			t.Errorf("%s: got %s, want %s", key, response.Asset[key], want)
		}
	}
	for _, key := range []string{"created_at", "updated_at"} {
		var stamp string
		if err := json.Unmarshal(response.Asset[key], &stamp); err != nil {
			t.Fatal(err)
		}
		if _, err := time.Parse(time.RFC3339, stamp); err != nil {
			t.Fatal(err)
		}
	}
	if string(response.Asset["id"]) == `""` || response.Asset["id"] == nil {
		t.Fatal("missing ID")
	}
	for _, filter := range []string{"", "?status=ACTIVE", "?status=active"} {
		listed := request(s, "GET", "/api/v1/assets"+filter, "")
		var list struct {
			Assets []map[string]json.RawMessage `json:"assets"`
		}
		if listed.Code != 200 {
			t.Fatalf("list: %d %s", listed.Code, listed.Body)
		}
		if err := json.Unmarshal(listed.Body.Bytes(), &list); err != nil {
			t.Fatal(err)
		}
		if len(list.Assets) != 1 || string(list.Assets[0]["id"]) != string(response.Asset["id"]) {
			t.Fatalf("list mismatch: %s", listed.Body)
		}
	}
	retired := request(s, "GET", "/api/v1/assets?status=RETIRED", "")
	if retired.Code != 200 || retired.Body.String() != `{"assets":[]}` {
		t.Fatalf("retired list: %d %s", retired.Code, retired.Body)
	}
	// The HTTP contract uses integer JSON numbers, including values above float64's exact range.
	large := request(s, "POST", "/api/v1/assets", `{"name":"Large","price_cents":9007199254740993,"purchase_date":"`+today+`"}`)
	if large.Code != 201 || !strings.Contains(large.Body.String(), `"price_cents":9007199254740993`) {
		t.Fatalf("integer precision: %d %s", large.Code, large.Body)
	}
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
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db))

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
	if unauthenticated := request(s, "GET", "/api/v1/assets", ""); unauthenticated.Code != 401 {
		t.Fatalf("asset API accepted unauthenticated request: %d %s", unauthenticated.Code, unauthenticated.Body)
	}
	ownedCreate := requestBearer(s, "POST", "/api/v1/assets", `{"name":"Owned","price_cents":100,"purchase_date":"2020-01-01"}`, alice)
	if ownedCreate.Code != 201 {
		t.Fatalf("owned asset create: %d %s", ownedCreate.Code, ownedCreate.Body)
	}
	var ownedPayload struct {
		Asset struct {
			ID string `json:"id"`
		} `json:"asset"`
	}
	if err := json.Unmarshal(ownedCreate.Body.Bytes(), &ownedPayload); err != nil {
		t.Fatal(err)
	}
	if bobAssets := requestBearer(s, "GET", "/api/v1/assets", "", bob); bobAssets.Code != 200 || bobAssets.Body.String() != `{"assets":[]}` {
		t.Fatalf("asset CRUD owner isolation failed: %d %s", bobAssets.Code, bobAssets.Body)
	}
	if bobGet := requestBearer(s, "GET", "/api/v1/assets/"+ownedPayload.Asset.ID, "", bob); bobGet.Code != 404 {
		t.Fatalf("cross-owner asset lookup was not hidden: %d %s", bobGet.Code, bobGet.Body)
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
	if alicePull.Code != 200 || !strings.Contains(alicePull.Body.String(), "Keyboard") || !strings.Contains(alicePull.Body.String(), "Owned") {
		t.Fatalf("owner pull missing asset: %d %s", alicePull.Code, alicePull.Body)
	}

	expiry := `{"cursor":0,"expiry_cursor":0,"idempotency_key":"expiry-1","changes":[],"expiry_changes":[{"id":"expiry-1","base_version":0,"name":"Milk","category":"Food","package_expiry_date":"2030-01-01","opened_date":"","opened_validity_days":0,"location":"Kitchen","notes":"","status":"IN_USE","archived_at":""}]}`
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
	if afterLogout.Code != 401 {
		t.Fatalf("revoked token: %d %s", afterLogout.Code, afterLogout.Body)
	}
}

func TestGetAssetByID(t *testing.T) {
	s, _ := testServer(t)
	today := time.Now().Format(time.DateOnly)
	created := request(s, "POST", "/api/v1/assets", `{"name":"Keyboard","price_cents":10000,"purchase_date":"`+today+`"}`)
	if created.Code != 201 {
		t.Fatalf("create: %d %s", created.Code, created.Body)
	}
	var createdResponse struct {
		Asset struct {
			ID string `json:"id"`
		} `json:"asset"`
	}
	if err := json.Unmarshal(created.Body.Bytes(), &createdResponse); err != nil {
		t.Fatal(err)
	}

	found := request(s, "GET", "/api/v1/assets/"+createdResponse.Asset.ID, "")
	if found.Code != 200 {
		t.Fatalf("get: %d %s", found.Code, found.Body)
	}
	var response struct {
		Asset map[string]json.RawMessage `json:"asset"`
	}
	if err := json.Unmarshal(found.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if string(response.Asset["id"]) != `"`+createdResponse.Asset.ID+`"` {
		t.Fatalf("id mismatch: %s", found.Body)
	}
	if string(response.Asset["name"]) != `"Keyboard"` || string(response.Asset["price_cents"]) != "10000" {
		t.Fatalf("payload mismatch: %s", found.Body)
	}

	missing := request(s, "GET", "/api/v1/assets/does-not-exist", "")
	if missing.Code != 404 {
		t.Fatalf("missing: got %d %s, want 404", missing.Code, missing.Body)
	}
	var failure struct {
		Error string `json:"error"`
	}
	if err := json.Unmarshal(missing.Body.Bytes(), &failure); err != nil || failure.Error == "" {
		t.Fatalf("expected JSON error: %s", missing.Body)
	}
}

func TestUpdateAssetByID(t *testing.T) {
	s, _ := testServer(t)
	today := time.Now().Format(time.DateOnly)
	created := request(s, "POST", "/api/v1/assets", `{"name":"Keyboard","price_cents":10000,"purchase_date":"`+today+`"}`)
	if created.Code != 201 {
		t.Fatalf("create: %d %s", created.Code, created.Body)
	}
	var createdResponse struct {
		Asset struct {
			ID string `json:"id"`
		} `json:"asset"`
	}
	if err := json.Unmarshal(created.Body.Bytes(), &createdResponse); err != nil {
		t.Fatal(err)
	}

	// Two days ago keeps held-days derived values deterministic regardless of run date.
	purchaseDate := time.Now().AddDate(0, 0, -2).Format(time.DateOnly)
	updated := request(s, "PUT", "/api/v1/assets/"+createdResponse.Asset.ID,
		`{"name":" Mechanical Keyboard ","price_cents":20000,"purchase_date":"`+purchaseDate+`"}`)
	if updated.Code != 200 {
		t.Fatalf("update: %d %s", updated.Code, updated.Body)
	}
	var response struct {
		Asset map[string]json.RawMessage `json:"asset"`
	}
	if err := json.Unmarshal(updated.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	for key, want := range map[string]string{
		"id":               `"` + createdResponse.Asset.ID + `"`,
		"name":             `"Mechanical Keyboard"`,
		"price_cents":      "20000",
		"purchase_date":    `"` + purchaseDate + `"`,
		"status":           `"ACTIVE"`,
		"held_days":        "3",
		"daily_cost_cents": "6667",
	} {
		if string(response.Asset[key]) != want {
			t.Errorf("%s: got %s, want %s", key, response.Asset[key], want)
		}
	}

	// The change is persisted and visible through GET.
	found := request(s, "GET", "/api/v1/assets/"+createdResponse.Asset.ID, "")
	if found.Code != 200 || !strings.Contains(found.Body.String(), `"name":"Mechanical Keyboard"`) ||
		!strings.Contains(found.Body.String(), `"price_cents":20000`) {
		t.Fatalf("get after update: %d %s", found.Code, found.Body)
	}

	missing := request(s, "PUT", "/api/v1/assets/does-not-exist",
		`{"name":"Keyboard","price_cents":10000,"purchase_date":"`+today+`"}`)
	if missing.Code != 404 {
		t.Fatalf("missing: got %d %s, want 404", missing.Code, missing.Body)
	}
	var failure struct {
		Error string `json:"error"`
	}
	if err := json.Unmarshal(missing.Body.Bytes(), &failure); err != nil || failure.Error == "" {
		t.Fatalf("expected JSON error: %s", missing.Body)
	}

	for _, tc := range []struct{ name, body string }{
		{"malformed", "{"},
		{"empty", "{}"},
		{"blank name", `{"name":"  ","price_cents":10000,"purchase_date":"2026-09-03"}`},
		{"negative price", `{"name":"A","price_cents":-1,"purchase_date":"2026-09-03"}`},
		{"invalid date", `{"name":"A","price_cents":10000,"purchase_date":"09/03/2026"}`},
	} {
		t.Run("invalid "+tc.name, func(t *testing.T) {
			got := request(s, "PUT", "/api/v1/assets/"+createdResponse.Asset.ID, tc.body)
			if got.Code != 400 {
				t.Fatalf("got %d: %s", got.Code, got.Body)
			}
			var bad struct {
				Error string `json:"error"`
			}
			if err := json.Unmarshal(got.Body.Bytes(), &bad); err != nil || bad.Error == "" {
				t.Fatalf("expected JSON error: %s", got.Body)
			}
		})
	}

	// Rejected updates must not mutate the stored asset.
	after := request(s, "GET", "/api/v1/assets/"+createdResponse.Asset.ID, "")
	if !strings.Contains(after.Body.String(), `"name":"Mechanical Keyboard"`) ||
		!strings.Contains(after.Body.String(), `"price_cents":20000`) {
		t.Fatalf("asset mutated by rejected update: %s", after.Body)
	}
}

func TestInvalidAssetRequests(t *testing.T) {
	s, _ := testServer(t)
	cases := []struct{ name, method, path, body string }{
		{"status", "GET", "/api/v1/assets?status=UNKNOWN", ""},
		{"numeric status", "GET", "/api/v1/assets?status=1", ""},
		{"malformed", "POST", "/api/v1/assets", "{"},
		{"empty", "POST", "/api/v1/assets", "{}"},
		{"blank name", "POST", "/api/v1/assets", `{"name":"  ","purchase_date":"2026-09-05"}`},
		{"negative price", "POST", "/api/v1/assets", `{"name":"A","price_cents":-1,"purchase_date":"2026-09-05"}`},
		{"fractional price", "POST", "/api/v1/assets", `{"name":"A","price_cents":1.5,"purchase_date":"2026-09-05"}`},
		{"overflow", "POST", "/api/v1/assets", `{"name":"A","price_cents":9223372036854775808,"purchase_date":"2026-09-05"}`},
		{"invalid date", "POST", "/api/v1/assets", `{"name":"A","purchase_date":"2026-02-30"}`},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := request(s, tc.method, tc.path, tc.body)
			var failure struct {
				Error string `json:"error"`
			}
			if got.Code != 400 {
				t.Fatalf("got %d: %s", got.Code, got.Body)
			}
			if err := json.Unmarshal(got.Body.Bytes(), &failure); err != nil || failure.Error == "" {
				t.Fatalf("expected JSON error: %s", got.Body)
			}
		})
	}
	listed := request(s, "GET", "/api/v1/assets", "")
	if listed.Body.String() != `{"assets":[]}` {
		t.Fatalf("invalid requests persisted data: %s", listed.Body)
	}
}

func TestDatabaseErrors(t *testing.T) {
	s, closeDB := testServer(t)
	closeDB()
	for _, tc := range []struct{ method, path string }{
		{"GET", "/api/v1/assets"},
		{"POST", "/api/v1/assets"},
		{"PUT", "/api/v1/assets/does-not-exist"},
	} {
		t.Run(tc.method, func(t *testing.T) {
			got := request(s, tc.method, tc.path, `{"name":"A","price_cents":100,"purchase_date":"2026-09-05"}`)
			if got.Code != 500 || got.Body.String() != `{"error":"internal server error"}` {
				t.Fatalf("database error: %d %s", got.Code, got.Body)
			}
		})
	}
}

func TestAssetStatusRoundTrip(t *testing.T) {
	s, closeDB := testServer(t)
	created := request(s, "POST", "/api/v1/assets", `{"name":"Keyboard","price_cents":10000,"purchase_date":"2020-01-01","image_url":"image"}`)
	var response struct {
		Asset service.AssetView `json:"asset"`
	}
	if created.Code != 201 {
		t.Fatalf("create: %d %s", created.Code, created.Body)
	}
	if err := json.Unmarshal(created.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	path := "/api/v1/assets/" + response.Asset.ID
	if !strings.Contains(created.Body.String(), `"retired_date":""`) {
		t.Fatal("missing explicit empty retirement date")
	}
	for _, body := range []string{
		`{`, `{}`, `{"status":1}`, `{"status":"UNKNOWN"}`, `{"status":"retired","retired_date":"2020-01-03"}`,
		`{"status":"RETIRED"}`, `{"status":"RETIRED","retired_date":"2020-02-30"}`,
		`{"status":"RETIRED","retired_date":"2019-12-31"}`,
		`{"status":"RETIRED","retired_date":"` + time.Now().AddDate(0, 0, 1).Format(time.DateOnly) + `"}`,
		`{"status":"ACTIVE","retired_date":"2020-01-03"}`,
	} {
		got := request(s, "PUT", path+"/status", body)
		if got.Code != 400 || !strings.Contains(got.Body.String(), `"error":`) {
			t.Fatalf("invalid %s: %d %s", body, got.Code, got.Body)
		}
	}
	for i := 0; i < 2; i++ {
		retired := request(s, "PUT", path+"/status", `{"status":"RETIRED","retired_date":"2020-01-03"}`)
		if retired.Code != 200 {
			t.Fatalf("retire: %d %s", retired.Code, retired.Body)
		}
		if err := json.Unmarshal(retired.Body.Bytes(), &response); err != nil {
			t.Fatal(err)
		}
		if response.Asset.Status != "RETIRED" || response.Asset.RetiredDate != "2020-01-03" || response.Asset.HeldDays != 3 || response.Asset.DailyCostCents != 3333 || response.Asset.ImageURL != "image" || response.Asset.Name != "Keyboard" {
			t.Fatalf("retired: %+v", response.Asset)
		}
	}
	found := request(s, "GET", path, "")
	if found.Code != 200 || !strings.Contains(found.Body.String(), `"retired_date":"2020-01-03"`) {
		t.Fatalf("not persisted: %s", found.Body)
	}
	retiredList := request(s, "GET", "/api/v1/assets?status=RETIRED", "")
	if !strings.Contains(retiredList.Body.String(), `"retired_date":"2020-01-03"`) {
		t.Fatal(retiredList.Body)
	}
	activeList := request(s, "GET", "/api/v1/assets?status=ACTIVE", "")
	if activeList.Body.String() != `{"assets":[]}` {
		t.Fatal(activeList.Body)
	}
	invalidEdit := request(s, "PUT", path, `{"name":"Edited","price_cents":0,"purchase_date":"2020-01-04"}`)
	if invalidEdit.Code != 400 {
		t.Fatalf("edit after retirement: %d %s", invalidEdit.Code, invalidEdit.Body)
	}
	active := request(s, "PUT", path+"/status", `{"status":"ACTIVE","retired_date":""}`)
	if active.Code != 200 {
		t.Fatalf("reactivate: %d %s", active.Code, active.Body)
	}
	if err := json.Unmarshal(active.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if response.Asset.Status != "ACTIVE" || response.Asset.RetiredDate != "" || response.Asset.HeldDays <= 3 || response.Asset.PriceCents != 10000 {
		t.Fatalf("reactivated: %+v", response.Asset)
	}
	found = request(s, "GET", path, "")
	if !strings.Contains(found.Body.String(), `"retired_date":""`) {
		t.Fatal("retirement date was not cleared in database")
	}
	missing := request(s, "PUT", "/api/v1/assets/missing/status", `{"status":"ACTIVE"}`)
	if missing.Code != 404 || !strings.Contains(missing.Body.String(), `"error":`) {
		t.Fatalf("missing: %d %s", missing.Code, missing.Body)
	}
	closeDB()
	failure := request(s, "PUT", path+"/status", `{"status":"ACTIVE"}`)
	if failure.Code != 500 || failure.Body.String() != `{"error":"internal server error"}` {
		t.Fatalf("db error: %d %s", failure.Code, failure.Body)
	}
}

func TestAssetArchiveRoundTrip(t *testing.T) {
	s, closeDB := testServer(t)
	created := request(s, "POST", "/api/v1/assets", `{"name":"Archive test","price_cents":1200,"purchase_date":"2020-01-01"}`)
	var response struct {
		Asset service.AssetView `json:"asset"`
	}
	if created.Code != 201 {
		t.Fatal(created.Body)
	}
	if err := json.Unmarshal(created.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	path := "/api/v1/assets/" + response.Asset.ID
	if !strings.Contains(created.Body.String(), `"archived_at":""`) {
		t.Fatal("missing explicit empty archive timestamp")
	}
	retired := request(s, "PUT", path+"/status", `{"status":"RETIRED","retired_date":"2020-01-03"}`)
	if retired.Code != 200 {
		t.Fatal(retired.Body)
	}
	for _, body := range []string{`{`, `{}`, `{"action":true}`, `{"action":"DELETE"}`} {
		got := request(s, "PUT", path+"/archive", body)
		if got.Code != 400 || !strings.Contains(got.Body.String(), `"error":`) {
			t.Fatalf("invalid: %d %s", got.Code, got.Body)
		}
	}
	firstStamp := ""
	for i := 0; i < 2; i++ {
		got := request(s, "PUT", path+"/archive", `{"action":"ARCHIVE"}`)
		if got.Code != 200 {
			t.Fatalf("archive: %d %s", got.Code, got.Body)
		}
		if err := json.Unmarshal(got.Body.Bytes(), &response); err != nil {
			t.Fatal(err)
		}
		if _, err := time.Parse(time.RFC3339, response.Asset.ArchivedAt); err != nil {
			t.Fatal(err)
		}
		if i == 0 {
			firstStamp = response.Asset.ArchivedAt
		} else if response.Asset.ArchivedAt != firstStamp {
			t.Fatal("duplicate archive changed timestamp")
		}
	}
	for _, query := range []string{"", "?scope=CURRENT", "?status=RETIRED"} {
		got := request(s, "GET", "/api/v1/assets"+query, "")
		if got.Code != 200 || got.Body.String() != `{"assets":[]}` {
			t.Fatalf("default includes archive: %s", got.Body)
		}
	}
	for _, query := range []string{"?scope=ARCHIVED", "?scope=ALL", "?scope=ARCHIVED&status=RETIRED"} {
		got := request(s, "GET", "/api/v1/assets"+query, "")
		if got.Code != 200 || !strings.Contains(got.Body.String(), `"archived_at":"`+firstStamp+`"`) {
			t.Fatalf("archive missing: %s", got.Body)
		}
	}
	if got := request(s, "GET", "/api/v1/assets?scope=INVALID", ""); got.Code != 400 {
		t.Fatalf("invalid scope: %d", got.Code)
	}
	for _, tc := range []struct{ suffix, body string }{
		{"", `{"name":"Changed","price_cents":0,"purchase_date":"2020-01-01"}`},
		{"/status", `{"status":"ACTIVE"}`},
	} {
		got := request(s, "PUT", path+tc.suffix, tc.body)
		if got.Code != 409 {
			t.Fatalf("archived write: %d %s", got.Code, got.Body)
		}
	}
	got := request(s, "GET", path, "")
	if got.Code != 200 {
		t.Fatal("archive not readable")
	}
	for i := 0; i < 2; i++ {
		restored := request(s, "PUT", path+"/archive", `{"action":"RESTORE"}`)
		if restored.Code != 200 {
			t.Fatal(restored.Body)
		}
		if err := json.Unmarshal(restored.Body.Bytes(), &response); err != nil {
			t.Fatal(err)
		}
		if response.Asset.ArchivedAt != "" || response.Asset.Status != "RETIRED" || response.Asset.RetiredDate != "2020-01-03" || response.Asset.Name != "Archive test" || response.Asset.PriceCents != 1200 || response.Asset.HeldDays != 3 {
			t.Fatalf("restore changed fields: %+v", response.Asset)
		}
	}
	current := request(s, "GET", "/api/v1/assets", "")
	if !strings.Contains(current.Body.String(), `"name":"Archive test"`) {
		t.Fatal("restoration not persisted")
	}
	archived := request(s, "GET", "/api/v1/assets?scope=ARCHIVED", "")
	if archived.Body.String() != `{"assets":[]}` {
		t.Fatal(archived.Body)
	}
	missing := request(s, "PUT", "/api/v1/assets/missing/archive", `{"action":"RESTORE"}`)
	if missing.Code != 404 {
		t.Fatalf("missing: %d", missing.Code)
	}
	closeDB()
	failed := request(s, "PUT", path+"/archive", `{"action":"ARCHIVE"}`)
	if failed.Code != 500 || failed.Body.String() != `{"error":"internal server error"}` {
		t.Fatalf("db error: %d %s", failed.Code, failed.Body)
	}
}

func TestAssetIconRoundTrip(t *testing.T) {
	s, _ := testServer(t)
	created := request(s, "POST", "/api/v1/assets", `{"name":"Camera","price_cents":100,"purchase_date":"2020-01-01","icon_key":"camera"}`)
	var response struct {
		Asset service.AssetView `json:"asset"`
	}
	if created.Code != 201 {
		t.Fatal(created.Body)
	}
	if err := json.Unmarshal(created.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if response.Asset.IconKey != "camera" {
		t.Fatal(response.Asset.IconKey)
	}
	path := "/api/v1/assets/" + response.Asset.ID
	for _, tc := range []struct{ body, icon string }{
		{`{"name":"Camera","price_cents":100,"purchase_date":"2020-01-01","icon_key":"phone"}`, "phone"},
		{`{"name":"Camera","price_cents":100,"purchase_date":"2020-01-01"}`, "phone"},
	} {
		got := request(s, "PUT", path, tc.body)
		if got.Code != 200 || !strings.Contains(got.Body.String(), `"icon_key":"`+tc.icon+`"`) {
			t.Fatalf("edit: %d %s", got.Code, got.Body)
		}
	}
	for _, method := range []string{"POST", "PUT"} {
		target := path
		if method == "POST" {
			target = "/api/v1/assets"
		}
		got := request(s, method, target, `{"name":"X","price_cents":0,"purchase_date":"2020-01-01","icon_key":"invalid"}`)
		if got.Code != 400 {
			t.Fatalf("invalid icon: %d %s", got.Code, got.Body)
		}
	}
	for _, tc := range []struct{ suffix, body string }{
		{"/status", `{"status":"RETIRED","retired_date":"2020-01-03"}`},
		{"/archive", `{"action":"ARCHIVE"}`},
		{"/archive", `{"action":"RESTORE"}`},
	} {
		got := request(s, "PUT", path+tc.suffix, tc.body)
		if got.Code != 200 || !strings.Contains(got.Body.String(), `"icon_key":"phone"`) {
			t.Fatalf("lifecycle icon: %d %s", got.Code, got.Body)
		}
	}
	for _, target := range []string{path, "/api/v1/assets"} {
		got := request(s, "GET", target, "")
		if got.Code != 200 || !strings.Contains(got.Body.String(), `"icon_key":"phone"`) {
			t.Fatalf("persisted icon: %s", got.Body)
		}
	}
	legacy := request(s, "POST", "/api/v1/assets", `{"name":"Old","price_cents":0,"purchase_date":"2020-01-01"}`)
	if legacy.Code != 201 || !strings.Contains(legacy.Body.String(), `"icon_key":"devices"`) {
		t.Fatal(legacy.Body)
	}
}
