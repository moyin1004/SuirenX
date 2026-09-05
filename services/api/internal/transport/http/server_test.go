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
	for _, method := range []string{"GET", "POST"} {
		t.Run(method, func(t *testing.T) {
			got := request(s, method, "/api/v1/assets", `{"name":"A","price_cents":100,"purchase_date":"2026-09-05"}`)
			if got.Code != 500 || got.Body.String() != `{"error":"internal server error"}` {
				t.Fatalf("database error: %d %s", got.Code, got.Body)
			}
		})
	}
}
