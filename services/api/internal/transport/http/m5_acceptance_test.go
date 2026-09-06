package http

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/database"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

func TestM5ConcurrentEditorsAndDatabaseRestore(t *testing.T) {
	directory := t.TempDir()
	databasePath := filepath.Join(directory, "original.db")
	db, err := database.Open(databasePath)
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db))
	tokenResponse := request(s, "POST", "/api/v1/auth/register", `{"username":"concurrent","password":"correct horse battery staple"}`)
	if tokenResponse.Code != 201 {
		t.Fatalf("register: %d %s", tokenResponse.Code, tokenResponse.Body)
	}
	var token struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(tokenResponse.Body.Bytes(), &token); err != nil || token.AccessToken == "" {
		t.Fatalf("token: %s", tokenResponse.Body)
	}

	create := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"initial","changes":[{"id":"device-asset","base_version":0,"name":"Original","price_cents":100,"purchase_date":"2020-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"devices","purchase_channel":"","warranty_end_date":"","notes":"","tags":[]}]}`, token.AccessToken)
	if create.Code != 200 || !strings.Contains(create.Body.String(), `"version":1`) {
		t.Fatalf("initial sync: %d %s", create.Code, create.Body)
	}
	update := func(key, name string) string {
		return `{"cursor":1,"idempotency_key":"` + key + `","changes":[{"id":"device-asset","base_version":1,"name":"` + name + `","price_cents":100,"purchase_date":"2020-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"devices","purchase_channel":"","warranty_end_date":"","notes":"","tags":[]}]}`
	}
	responses := make(chan string, 2)
	var group sync.WaitGroup
	for _, item := range []struct{ key, name string }{{"editor-a", "Phone A"}, {"editor-b", "Phone B"}} {
		group.Add(1)
		go func(key, name string) {
			defer group.Done()
			responses <- requestBearer(s, "POST", "/api/v1/sync/assets", update(key, name), token.AccessToken).Body.String()
		}(item.key, item.name)
	}
	group.Wait()
	close(responses)
	conflicts := 0
	for body := range responses {
		if strings.Contains(body, `"conflicts":[{`) {
			conflicts++
		}
	}
	if conflicts != 1 {
		t.Fatalf("concurrent updates should produce one conflict, got %d", conflicts)
	}
	if err := sqlDB.Close(); err != nil {
		t.Fatal(err)
	}

	recoveredPath := filepath.Join(directory, "recovered.db")
	contents, err := os.ReadFile(databasePath)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(recoveredPath, contents, 0o600); err != nil {
		t.Fatal(err)
	}
	recovered, err := database.Open(recoveredPath)
	if err != nil {
		t.Fatal(err)
	}
	recoveredSQL, err := recovered.DB()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = recoveredSQL.Close() })
	recoveredServer := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(recovered)), WithM5(recovered))
	pull := requestBearer(recoveredServer, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"after-restore","changes":[]}`, token.AccessToken)
	if pull.Code != 200 || !strings.Contains(pull.Body.String(), "device-asset") {
		t.Fatalf("restored database lost sync data: %d %s", pull.Code, pull.Body)
	}
}
