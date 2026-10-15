package http

import (
	"encoding/json"
	"fmt"
	"strings"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

// adminAccessToken bootstraps the configured superadmin and returns its bearer
// token for tests that need an authenticated administrator.
func adminAccessToken(t *testing.T, s *Server) string {
	t.Helper()
	response := request(s, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	if response.Code != 200 {
		t.Fatalf("admin login: %d %s", response.Code, response.Body)
	}
	var token struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &token); err != nil || token.AccessToken == "" {
		t.Fatalf("admin token: %s (%v)", response.Body, err)
	}
	return token.AccessToken
}

func TestLoginAttemptsAreRateLimitedOverHTTP(t *testing.T) {
	s := NewServer(":0", service.NewAssetService(nil), WithM5(openAuthTestDB(t), testJWTSecret))
	register := request(s, "POST", "/api/v1/auth/register", `{"username":"member","password":"correct member password"}`)
	if register.Code != 201 {
		t.Fatalf("register: %d %s", register.Code, register.Body)
	}
	limited := false
	for attempt := 0; attempt < 64; attempt++ {
		response := request(s, "POST", "/api/v1/auth/login", `{"username":"member","password":"wrong member password"}`)
		if response.Code == 429 {
			if !strings.Contains(response.Body.String(), "too_many_attempts") {
				t.Fatalf("rate limited login used an unexpected envelope: %s", response.Body)
			}
			limited = true
			break
		}
		if response.Code != 401 {
			t.Fatalf("attempt %d: unexpected status %d %s", attempt, response.Code, response.Body)
		}
	}
	if !limited {
		t.Fatal("repeated login attempts were never rate limited")
	}
}

func TestRegistrationAttemptsAreRateLimitedOverHTTP(t *testing.T) {
	s := NewServer(":0", service.NewAssetService(nil), WithM5(openAuthTestDB(t), testJWTSecret))
	first := request(s, "POST", "/api/v1/auth/register", `{"username":"member","password":"correct member password"}`)
	if first.Code != 201 {
		t.Fatalf("register: %d %s", first.Code, first.Body)
	}
	limited := false
	for attempt := 0; attempt < 16; attempt++ {
		response := request(s, "POST", "/api/v1/auth/register", `{"username":"member","password":"correct member password"}`)
		if response.Code == 429 {
			limited = true
			break
		}
		// The explicit message stays part of the client contract; the budget is
		// what makes bulk enumeration impractical.
		if response.Code != 400 || !strings.Contains(response.Body.String(), "username is already registered") {
			t.Fatalf("attempt %d: unexpected status %d %s", attempt, response.Code, response.Body)
		}
	}
	if !limited {
		t.Fatal("repeated registration attempts were never rate limited")
	}
}

func TestConfigSizeLimitIsCappedAtTenMiB(t *testing.T) {
	t.Setenv("SUIRENX_ADMIN_USERNAME", "operator")
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "first secure admin secret")
	db := openAuthTestDB(t)
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	token := adminAccessToken(t, s)

	overCap := requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes", fmt.Sprintf(`{"max_bytes":%d}`, domain.MaxConfigContentBytes+1), token)
	if overCap.Code != 400 || !strings.Contains(overCap.Body.String(), "invalid_config_limit") {
		t.Fatalf("limit above the cap: %d %s", overCap.Code, overCap.Body)
	}
	legacy := requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes", `{"max_bytes":104857600}`, token)
	if legacy.Code != 400 {
		t.Fatalf("former 100 MiB limit is still accepted: %d %s", legacy.Code, legacy.Body)
	}
	atCap := requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes", fmt.Sprintf(`{"max_bytes":%d}`, domain.MaxConfigContentBytes), token)
	if atCap.Code != 200 || !strings.Contains(atCap.Body.String(), fmt.Sprintf(`"max_bytes":%d`, domain.MaxConfigContentBytes)) {
		t.Fatalf("limit at the cap: %d %s", atCap.Code, atCap.Body)
	}
	// A value stored by an earlier build must not raise the effective limit.
	if err := db.Exec("UPDATE server_settings SET setting_value = ? WHERE setting_key = ?", "104857600", "config_max_bytes").Error; err != nil {
		t.Fatal(err)
	}
	read := requestBearer(s, "GET", "/api/v1/admin/settings/config-max-bytes", "", token)
	if read.Code != 200 || !strings.Contains(read.Body.String(), fmt.Sprintf(`"max_bytes":%d`, domain.MaxConfigContentBytes)) {
		t.Fatalf("legacy oversized setting was not clamped: %d %s", read.Code, read.Body)
	}
	// The transport must be able to carry a configuration body at the cap.
	if httpMaxRequestBodySize <= int(domain.MaxConfigContentBytes) {
		t.Fatalf("request body limit %d cannot carry a %d byte configuration", httpMaxRequestBodySize, domain.MaxConfigContentBytes)
	}
}
