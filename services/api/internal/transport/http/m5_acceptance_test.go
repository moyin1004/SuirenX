package http

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/common/ut"
	"github.com/moyin1004/suirenx/services/api/internal/auth"
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
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
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
	type syncResponse struct {
		code int
		body string
	}
	responses := make(chan syncResponse, 2)
	start := make(chan struct{})
	var group sync.WaitGroup
	for _, item := range []struct{ key, name string }{{"editor-a", "Phone A"}, {"editor-b", "Phone B"}} {
		group.Add(1)
		go func(key, name string) {
			defer group.Done()
			<-start
			response := requestBearer(s, "POST", "/api/v1/sync/assets", update(key, name), token.AccessToken)
			responses <- syncResponse{response.Code, response.Body.String()}
		}(item.key, item.name)
	}
	close(start)
	group.Wait()
	close(responses)
	conflicts := 0
	for response := range responses {
		if response.code != 200 {
			t.Fatalf("concurrent sync: status %d, body %s", response.code, response.body)
		}
		if strings.Contains(response.body, `"conflicts":[{`) {
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
	recoveredServer := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(recovered)), WithM5(recovered, testJWTSecret))
	pull := requestBearer(recoveredServer, "POST", "/api/v1/sync/assets", `{"cursor":0,"idempotency_key":"after-restore","changes":[]}`, token.AccessToken)
	if pull.Code != 200 || !strings.Contains(pull.Body.String(), "device-asset") {
		t.Fatalf("restored database lost sync data: %d %s", pull.Code, pull.Body)
	}
}

func TestSuperadminBootstrapAndAdminLoginFailClosed(t *testing.T) {
	directory := t.TempDir()
	db, err := database.Open(filepath.Join(directory, "admin.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		sqlDB, _ := db.DB()
		_ = sqlDB.Close()
	}()
	t.Setenv("SUIRENX_ADMIN_USERNAME", "operator")
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "first secure admin secret")
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	admin := request(s, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	if admin.Code != 200 || !strings.Contains(admin.Body.String(), `"access_token"`) {
		t.Fatalf("admin login: %d %s", admin.Code, admin.Body)
	}
	var adminToken struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(admin.Body.Bytes(), &adminToken); err != nil {
		t.Fatal(err)
	}
	register := request(s, "POST", "/api/v1/auth/register", `{"username":"member","password":"correct member password"}`)
	if register.Code != 201 {
		t.Fatalf("register user: %d %s", register.Code, register.Body)
	}
	var memberToken struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(register.Body.Bytes(), &memberToken); err != nil || memberToken.AccessToken == "" {
		t.Fatalf("member token: %s (%v)", register.Body, err)
	}
	registrationSetting := requestBearer(s, "GET", "/api/v1/admin/settings/account-registration", "", adminToken.AccessToken)
	if registrationSetting.Code != 200 || !strings.Contains(registrationSetting.Body.String(), `"enabled":true`) {
		t.Fatalf("new installations should allow registration: %d %s", registrationSetting.Code, registrationSetting.Body)
	}
	registrationSetting = requestBearer(s, "PUT", "/api/v1/admin/settings/account-registration", `{"enabled":false}`, adminToken.AccessToken)
	if registrationSetting.Code != 200 || !strings.Contains(registrationSetting.Body.String(), `"enabled":false`) {
		t.Fatalf("disable account registration: %d %s", registrationSetting.Code, registrationSetting.Body)
	}
	blocked := request(s, "POST", "/api/v1/auth/register", `{"username":"late-member","password":"correct late password"}`)
	if blocked.Code != 403 || !strings.Contains(blocked.Body.String(), "registration_closed") {
		t.Fatalf("closed registration should be rejected: %d %s", blocked.Code, blocked.Body)
	}
	memberLogin := request(s, "POST", "/api/v1/auth/login", `{"username":"member","password":"correct member password"}`)
	if memberLogin.Code != 200 {
		t.Fatalf("closing signup must keep existing account login available: %d %s", memberLogin.Code, memberLogin.Body)
	}
	registrationSetting = requestBearer(s, "PUT", "/api/v1/admin/settings/account-registration", `{"enabled":true}`, adminToken.AccessToken)
	if registrationSetting.Code != 200 {
		t.Fatalf("re-enable account registration: %d %s", registrationSetting.Code, registrationSetting.Body)
	}
	reopened := request(s, "POST", "/api/v1/auth/register", `{"username":"reopened-member","password":"correct reopened password"}`)
	if reopened.Code != 201 {
		t.Fatalf("registration should work after being re-enabled: %d %s", reopened.Code, reopened.Body)
	}
	memberAdminLogin := request(s, "POST", "/api/v1/admin/auth/login", `{"username":"member","password":"correct member password"}`)
	if memberAdminLogin.Code != 403 {
		t.Fatalf("ordinary user entered the admin area: %d %s", memberAdminLogin.Code, memberAdminLogin.Body)
	}
	accounts := requestBearer(s, "GET", "/api/v1/admin/accounts", "", adminToken.AccessToken)
	if accounts.Code != 200 || strings.Contains(accounts.Body.String(), "password_hash") {
		t.Fatalf("admin account list leaked sensitive data or failed: %d %s", accounts.Code, accounts.Body)
	}
	var accountList struct {
		Accounts []struct {
			ID       string `json:"id"`
			Username string `json:"username"`
		} `json:"accounts"`
	}
	if err := json.Unmarshal(accounts.Body.Bytes(), &accountList); err != nil {
		t.Fatal(err)
	}
	var memberID, superadminID string
	for _, account := range accountList.Accounts {
		if account.Username == "member" {
			memberID = account.ID
		} else if account.Username == "operator" {
			superadminID = account.ID
		}
	}
	if memberID == "" || superadminID == "" {
		t.Fatalf("member missing from admin account list: %s", accounts.Body)
	}
	setStatus := requestBearer(s, "PATCH", "/api/v1/admin/accounts/"+memberID+"/status", `{"disabled":true}`, adminToken.AccessToken)
	if setStatus.Code != 200 {
		t.Fatalf("disable member: %d %s", setStatus.Code, setStatus.Body)
	}
	disabledLogin := request(s, "POST", "/api/v1/auth/login", `{"username":"member","password":"correct member password"}`)
	if disabledLogin.Code != 401 || !strings.Contains(disabledLogin.Body.String(), "account_disabled") {
		t.Fatalf("disabled account logged in: %d %s", disabledLogin.Code, disabledLogin.Body)
	}
	currentJWT := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"expiry_cursor":0,"idempotency_key":"disabled-existing-jwt","changes":[],"expiry_changes":[]}`, memberToken.AccessToken)
	if currentJWT.Code != 200 {
		t.Fatalf("disabling an account revoked its still-valid JWT: %d %s", currentJWT.Code, currentJWT.Body)
	}
	lastAdmin := requestBearer(s, "PATCH", "/api/v1/admin/accounts/"+superadminID+"/status", `{"disabled":true}`, adminToken.AccessToken)
	if lastAdmin.Code != 409 {
		t.Fatalf("last superadmin could be disabled: %d %s", lastAdmin.Code, lastAdmin.Body)
	}
	for i := 0; i < 105; i++ {
		disabled := i%2 == 0
		value := "false"
		if disabled {
			value = "true"
		}
		response := requestBearer(s, "PATCH", "/api/v1/admin/accounts/"+memberID+"/status", `{"disabled":`+value+`}`, adminToken.AccessToken)
		if response.Code != 200 {
			t.Fatalf("account status audit %d: %d %s", i, response.Code, response.Body)
		}
	}
	var auditCount int64
	if err := db.Table("admin_audit_log").Count(&auditCount).Error; err != nil || auditCount != 100 {
		t.Fatalf("audit retention = %d, err=%v; want exactly the most recent 100", auditCount, err)
	}

	// A changed environment value is not an implicit password rotation.
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "second secure admin secret")
	secondServer := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	newPassword := request(secondServer, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"second secure admin secret"}`)
	if newPassword.Code != 401 {
		t.Fatalf("ordinary restart silently rotated admin password: %d %s", newPassword.Code, newPassword.Body)
	}
	oldPassword := request(secondServer, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	if oldPassword.Code != 200 {
		t.Fatalf("original password stopped working: %d %s", oldPassword.Code, oldPassword.Body)
	}

	t.Setenv("SUIRENX_ADMIN_USERNAME", "")
	missingConfigServer := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	unavailable := request(missingConfigServer, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	if unavailable.Code != 503 || !strings.Contains(unavailable.Body.String(), "SUIRENX_ADMIN_USERNAME") {
		t.Fatalf("missing bootstrap config did not fail closed: %d %s", unavailable.Code, unavailable.Body)
	}
}

func TestSuperadminConfigFilesAndScopedApiTokens(t *testing.T) {
	t.Setenv("SUIRENX_ADMIN_USERNAME", "operator")
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "first secure admin secret")
	db, err := database.Open(filepath.Join(t.TempDir(), "config.db"))
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = sqlDB.Close() })
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	login := request(s, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	var adminToken struct {
		AccessToken string `json:"access_token"`
	}
	if login.Code != 200 || json.Unmarshal(login.Body.Bytes(), &adminToken) != nil {
		t.Fatalf("admin login: %d %s", login.Code, login.Body)
	}

	create := requestBearer(s, "POST", "/api/v1/admin/configs", `{"display_name":"Mihomo primary","content":"proxies:\n  - name: edge\n"}`, adminToken.AccessToken)
	if create.Code != 201 {
		t.Fatalf("create config: %d %s", create.Code, create.Body)
	}
	var config struct {
		Key string `json:"config_key"`
	}
	if err := json.Unmarshal(create.Body.Bytes(), &config); err != nil || config.Key == "" {
		t.Fatalf("created config key: %s (%v)", create.Body, err)
	}
	list := requestBearer(s, "GET", "/api/v1/admin/configs", "", adminToken.AccessToken)
	if list.Code != 200 || strings.Contains(list.Body.String(), `"content"`) {
		t.Fatalf("config list should omit full contents: %d %s", list.Code, list.Body)
	}
	limit := requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes", `{"max_bytes":8}`, adminToken.AccessToken)
	if limit.Code != 200 || !strings.Contains(limit.Body.String(), `"max_bytes":8`) {
		t.Fatalf("configure global content limit: %d %s", limit.Code, limit.Body)
	}
	oversized := requestBearer(s, "POST", "/api/v1/admin/configs", `{"display_name":"too-large","content":"123456789"}`, adminToken.AccessToken)
	if oversized.Code != 413 {
		t.Fatalf("oversized config accepted: %d %s", oversized.Code, oversized.Body)
	}
	limit = requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes", `{"max_bytes":1024}`, adminToken.AccessToken)
	if limit.Code != 200 {
		t.Fatalf("restore content limit after boundary check: %d %s", limit.Code, limit.Body)
	}
	secondConfigResponse := requestBearer(s, "POST", "/api/v1/admin/configs", `{"display_name":"Mihomo secondary","content":"mixed-port: 7890\n"}`, adminToken.AccessToken)
	if secondConfigResponse.Code != 201 {
		t.Fatalf("create second config for token scope update: %d %s", secondConfigResponse.Code, secondConfigResponse.Body)
	}
	var secondConfig struct {
		Key string `json:"config_key"`
	}
	if err := json.Unmarshal(secondConfigResponse.Body.Bytes(), &secondConfig); err != nil || secondConfig.Key == "" {
		t.Fatalf("second config key: %s (%v)", secondConfigResponse.Body, err)
	}

	expiresAt := time.Now().UTC().Add(time.Hour).Format(time.RFC3339)
	tokenResponse := requestBearer(s, "POST", "/api/v1/admin/config-tokens", `{"label":"mihomo-home","transport_mode":"BEARER","config_keys":["`+config.Key+`"],"expires_at":"`+expiresAt+`"}`, adminToken.AccessToken)
	if tokenResponse.Code != 201 {
		t.Fatalf("create token: %d %s", tokenResponse.Code, tokenResponse.Body)
	}
	var issued struct {
		ID    string `json:"id"`
		Token string `json:"token"`
	}
	if err := json.Unmarshal(tokenResponse.Body.Bytes(), &issued); err != nil || issued.Token == "" || issued.ID == "" {
		t.Fatalf("one-time token response: %s (%v)", tokenResponse.Body, err)
	}
	listedTokens := requestBearer(s, "GET", "/api/v1/admin/config-tokens", "", adminToken.AccessToken)
	if listedTokens.Code != 200 || strings.Contains(listedTokens.Body.String(), issued.Token) || strings.Contains(listedTokens.Body.String(), "token_hash") {
		t.Fatalf("token list exposed token material: %d %s", listedTokens.Code, listedTokens.Body)
	}

	content := requestBearer(s, "GET", "/api/v1/configs/"+config.Key+"/content", "", issued.Token)
	if content.Code != 200 || content.Body.String() != "proxies:\n  - name: edge\n" {
		t.Fatalf("config read was not exact UTF-8 content: %d %q", content.Code, content.Body.String())
	}
	if content.Header().Get("Content-Type") != "text/plain; charset=utf-8" || content.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("config response headers: %#v", content.Header())
	}
	deniedScope := requestBearer(s, "GET", "/api/v1/configs/cfg_not_authorized/content", "", issued.Token)
	if deniedScope.Code != 403 || !strings.Contains(deniedScope.Body.String(), "token_scope_denied") {
		t.Fatalf("token read outside its configured scope: %d %s", deniedScope.Code, deniedScope.Body)
	}
	wrongMode := request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+issued.Token, "")
	if wrongMode.Code != 401 || !strings.Contains(wrongMode.Body.String(), "invalid_api_token") {
		t.Fatalf("bearer token was accepted via query string: %d %s", wrongMode.Code, wrongMode.Body)
	}
	updatedToken := requestBearer(s, "PUT", "/api/v1/admin/config-tokens/"+issued.ID, `{"label":"mihomo-home-updated","config_keys":["`+secondConfig.Key+`"]}`, adminToken.AccessToken)
	if updatedToken.Code != 200 {
		t.Fatalf("update token name and config scopes: %d %s", updatedToken.Code, updatedToken.Body)
	}
	removedScope := requestBearer(s, "GET", "/api/v1/configs/"+config.Key+"/content", "", issued.Token)
	if removedScope.Code != 403 || !strings.Contains(removedScope.Body.String(), "token_scope_denied") {
		t.Fatalf("removed token scope remained readable: %d %s", removedScope.Code, removedScope.Body)
	}
	addedScope := requestBearer(s, "GET", "/api/v1/configs/"+secondConfig.Key+"/content", "", issued.Token)
	if addedScope.Code != 200 || addedScope.Body.String() != "mixed-port: 7890\n" {
		t.Fatalf("updated token scope cannot read newly authorized config: %d %q", addedScope.Code, addedScope.Body.String())
	}
	var scopeAuditCount int64
	if err := db.Raw(`SELECT count(*) FROM admin_audit_log WHERE target_type = 'api_token' AND target_id = ? AND action = 'API_TOKEN_UPDATED'`, issued.ID).Scan(&scopeAuditCount).Error; err != nil || scopeAuditCount != 1 {
		t.Fatalf("token metadata change audit: count=%d err=%v", scopeAuditCount, err)
	}
	updatedTokenList := requestBearer(s, "GET", "/api/v1/admin/config-tokens", "", adminToken.AccessToken)
	if updatedTokenList.Code != 200 || !strings.Contains(updatedTokenList.Body.String(), `"label":"mihomo-home-updated"`) || !strings.Contains(updatedTokenList.Body.String(), `"config_keys":["`+secondConfig.Key+`"]`) {
		t.Fatalf("updated token metadata not persisted: %d %s", updatedTokenList.Code, updatedTokenList.Body)
	}
	emptyScope := requestBearer(s, "PUT", "/api/v1/admin/config-tokens/"+issued.ID, `{"label":"mihomo-home-updated","config_keys":[]}`, adminToken.AccessToken)
	if emptyScope.Code != 400 {
		t.Fatalf("empty token scopes should be rejected: %d %s", emptyScope.Code, emptyScope.Body)
	}
	unknownScope := requestBearer(s, "PUT", "/api/v1/admin/config-tokens/"+issued.ID, `{"label":"mihomo-home-updated","config_keys":["cfg_missing"]}`, adminToken.AccessToken)
	if unknownScope.Code != 404 || !strings.Contains(unknownScope.Body.String(), "config_not_found") {
		t.Fatalf("unknown config should be rejected when changing scopes: %d %s", unknownScope.Code, unknownScope.Body)
	}
	retainedScope := requestBearer(s, "GET", "/api/v1/configs/"+secondConfig.Key+"/content", "", issued.Token)
	if retainedScope.Code != 200 {
		t.Fatalf("failed scope update changed current grants: %d %s", retainedScope.Code, retainedScope.Body)
	}

	queryTokenResponse := requestBearer(s, "POST", "/api/v1/admin/config-tokens", `{"label":"query-reader","transport_mode":"QUERY","config_keys":["`+config.Key+`"]}`, adminToken.AccessToken)
	var queryIssued struct{ ID, Token string }
	if queryTokenResponse.Code != 201 || json.Unmarshal(queryTokenResponse.Body.Bytes(), &queryIssued) != nil {
		t.Fatalf("create query token: %d %s", queryTokenResponse.Code, queryTokenResponse.Body)
	}
	queryContent := request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+queryIssued.Token, "")
	if queryContent.Code != 200 || queryContent.Body.String() != "proxies:\n  - name: edge\n" {
		t.Fatalf("query token read: %d %q", queryContent.Code, queryContent.Body.String())
	}
	revoked := requestBearer(s, "DELETE", "/api/v1/admin/config-tokens/"+issued.ID, "", adminToken.AccessToken)
	if revoked.Code != 200 {
		t.Fatalf("revoke token: %d %s", revoked.Code, revoked.Body)
	}
	revokedUpdate := requestBearer(s, "PUT", "/api/v1/admin/config-tokens/"+issued.ID, `{"label":"renamed","config_keys":["`+config.Key+`"]}`, adminToken.AccessToken)
	if revokedUpdate.Code != 409 || !strings.Contains(revokedUpdate.Body.String(), "api_token_revoked") {
		t.Fatalf("revoked token should not be editable: %d %s", revokedUpdate.Code, revokedUpdate.Body)
	}
	denied := requestBearer(s, "GET", "/api/v1/configs/"+config.Key+"/content", "", issued.Token)
	if denied.Code != 401 || !strings.Contains(denied.Body.String(), "api_token_revoked") {
		t.Fatalf("revoked token still read content: %d %s", denied.Code, denied.Body)
	}

	updated := requestBearer(s, "PUT", "/api/v1/admin/configs/"+config.Key, `{"display_name":"Mihomo renamed","content":"new: value\n"}`, adminToken.AccessToken)
	if updated.Code != 200 {
		t.Fatalf("update/rename config: %d %s", updated.Code, updated.Body)
	}
	queryContent = request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+queryIssued.Token, "")
	if queryContent.Code != 200 || queryContent.Body.String() != "new: value\n" {
		t.Fatalf("rename changed token scope or content was not replaced: %d %q", queryContent.Code, queryContent.Body.String())
	}
	rateLimitedTokenResponse := requestBearer(s, "POST", "/api/v1/admin/config-tokens", `{"label":"rate-limit-reader","transport_mode":"QUERY","config_keys":["`+config.Key+`"]}`, adminToken.AccessToken)
	var rateLimitedToken struct{ Token string }
	if rateLimitedTokenResponse.Code != 201 || json.Unmarshal(rateLimitedTokenResponse.Body.Bytes(), &rateLimitedToken) != nil || rateLimitedToken.Token == "" {
		t.Fatalf("create rate limit token: %d %s", rateLimitedTokenResponse.Code, rateLimitedTokenResponse.Body)
	}
	for requestIndex := 1; requestIndex <= 61; requestIndex++ {
		limited := request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+rateLimitedToken.Token, "")
		if requestIndex <= 60 && limited.Code != 200 {
			t.Fatalf("content request %d should be allowed: %d %s", requestIndex, limited.Code, limited.Body)
		}
		if requestIndex == 61 && (limited.Code != 429 || !strings.Contains(limited.Body.String(), "rate_limited")) {
			t.Fatalf("61st content request should be rate limited: %d %s", limited.Code, limited.Body)
		}
	}
	deleted := requestBearer(s, "DELETE", "/api/v1/admin/configs/"+config.Key, "", adminToken.AccessToken)
	if deleted.Code != 200 {
		t.Fatalf("delete config: %d %s", deleted.Code, deleted.Body)
	}
	missing := request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+queryIssued.Token, "")
	if missing.Code != 404 {
		t.Fatalf("deleted key should return stable not-found: %d %s", missing.Code, missing.Body)
	}
	replacementResponse := requestBearer(s, "POST", "/api/v1/admin/configs", `{"display_name":"Mihomo primary","content":"replacement: true\n"}`, adminToken.AccessToken)
	var replacementConfig struct {
		Key string `json:"config_key"`
	}
	if replacementResponse.Code != 201 || json.Unmarshal(replacementResponse.Body.Bytes(), &replacementConfig) != nil || replacementConfig.Key == "" || replacementConfig.Key == config.Key {
		t.Fatalf("recreate deleted name with a fresh opaque key: %d %s old_key=%q", replacementResponse.Code, replacementResponse.Body, config.Key)
	}
	oldGrant := request(s, "GET", "/api/v1/configs/"+replacementConfig.Key+"/content?token="+queryIssued.Token, "")
	if oldGrant.Code != 403 || !strings.Contains(oldGrant.Body.String(), "token_scope_denied") {
		t.Fatalf("old token scope unexpectedly applied to replacement config: %d %s", oldGrant.Code, oldGrant.Body)
	}
	if err := db.Model(&repository.ApiTokenRecord{}).Where("id = ?", queryIssued.ID).Update("expires_at", time.Now().UTC().Add(-time.Minute)).Error; err != nil {
		t.Fatal(err)
	}
	expired := request(s, "GET", "/api/v1/configs/"+config.Key+"/content?token="+queryIssued.Token, "")
	if expired.Code != 401 || !strings.Contains(expired.Body.String(), "api_token_expired") {
		t.Fatalf("expired token did not return its stable error: %d %s", expired.Code, expired.Body)
	}
}

func TestWebAssetExpiryWritesAreVersionedIdempotentAndSyncable(t *testing.T) {
	t.Setenv("SUIRENX_ADMIN_USERNAME", "operator")
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "first secure admin secret")
	db, err := database.Open(filepath.Join(t.TempDir(), "web.db"))
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = sqlDB.Close() })
	s := NewServer(":0", service.NewAssetService(repository.NewGormAssetRepository(db)), WithM5(db, testJWTSecret))
	register := request(s, "POST", "/api/v1/auth/register", `{"username":"web-user","password":"correct web user password"}`)
	var userToken struct {
		AccessToken string `json:"access_token"`
	}
	if register.Code != 201 || json.Unmarshal(register.Body.Bytes(), &userToken) != nil {
		t.Fatalf("register: %d %s", register.Code, register.Body)
	}
	assetBody := `{"name":"Web laptop","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"browser entry","tags":["work"]}`
	create := requestWithHeaders(s, "POST", "/api/v1/web/assets", assetBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-create-asset-1"})
	if create.Code != 201 {
		t.Fatalf("create web asset: %d %s", create.Code, create.Body)
	}
	var asset struct {
		ID      string `json:"id"`
		Version int64  `json:"version"`
	}
	if err := json.Unmarshal(create.Body.Bytes(), &asset); err != nil || asset.ID == "" || asset.Version != 1 {
		t.Fatalf("created asset version: %s err=%v", create.Body, err)
	}
	retry := requestWithHeaders(s, "POST", "/api/v1/web/assets", assetBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-create-asset-1"})
	var retriedAsset struct {
		ID string `json:"id"`
	}
	if retry.Code != 201 || json.Unmarshal(retry.Body.Bytes(), &retriedAsset) != nil || retriedAsset.ID != asset.ID {
		t.Fatalf("same idempotency key did not replay response: %d %s", retry.Code, retry.Body)
	}
	archiveAssetBody := `{"base_version":1,"name":"Web laptop","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"2026-10-02T12:00:00Z","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"browser entry","tags":["work"]}`
	updatePath := "/api/v1/web/assets/" + asset.ID
	archiveAsset := requestWithHeaders(s, "PUT", updatePath, archiveAssetBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-archive-asset-2"})
	if archiveAsset.Code != 200 || !strings.Contains(archiveAsset.Body.String(), `"version":2`) {
		t.Fatalf("archive web asset: %d %s", archiveAsset.Code, archiveAsset.Body)
	}
	defaultAssets := requestBearer(s, "GET", "/api/v1/web/assets", "", userToken.AccessToken)
	archivedAssets := requestBearer(s, "GET", "/api/v1/web/assets?scope=ARCHIVED", "", userToken.AccessToken)
	archivedAssetDetail := requestBearer(s, "GET", updatePath, "", userToken.AccessToken)
	if defaultAssets.Code != 200 || strings.Contains(defaultAssets.Body.String(), asset.ID) || archivedAssets.Code != 200 || !strings.Contains(archivedAssets.Body.String(), asset.ID) || archivedAssetDetail.Code != 200 {
		t.Fatalf("archive visibility/read behavior: default=%d %s archived=%d %s detail=%d %s", defaultAssets.Code, defaultAssets.Body, archivedAssets.Code, archivedAssets.Body, archivedAssetDetail.Code, archivedAssetDetail.Body)
	}
	editWhileArchivedBody := `{"base_version":2,"name":"Web laptop Mk2","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"2026-10-02T12:00:00Z","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"updated in browser","tags":["work"]}`
	editWhileArchived := requestWithHeaders(s, "PUT", updatePath, editWhileArchivedBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-edit-archived-asset"})
	if editWhileArchived.Code != 409 || !strings.Contains(editWhileArchived.Body.String(), "restore_before_edit") {
		t.Fatalf("archived asset was editable: %d %s", editWhileArchived.Code, editWhileArchived.Body)
	}
	restoreAndEditBody := `{"base_version":2,"name":"Web laptop Mk2","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"updated in browser","tags":["work"]}`
	restoreAndEdit := requestWithHeaders(s, "PUT", updatePath, restoreAndEditBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-restore-edit-asset"})
	if restoreAndEdit.Code != 409 || !strings.Contains(restoreAndEdit.Body.String(), "restore_before_edit") {
		t.Fatalf("asset edit was combined with restore: %d %s", restoreAndEdit.Code, restoreAndEdit.Body)
	}
	restoreAssetBody := `{"base_version":2,"name":"Web laptop","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"browser entry","tags":["work"]}`
	restoredAsset := requestWithHeaders(s, "PUT", updatePath, restoreAssetBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-restore-asset-3"})
	if restoredAsset.Code != 200 || !strings.Contains(restoredAsset.Body.String(), `"version":3`) {
		t.Fatalf("restore archived asset: %d %s", restoredAsset.Code, restoredAsset.Body)
	}

	updateBody := `{"base_version":3,"name":"Web laptop Mk2","price_cents":129900,"purchase_date":"2025-01-01","status":"ACTIVE","image_url":"","retired_date":"","archived_at":"","icon_key":"laptop","purchase_channel":"shop","warranty_end_date":"2027-01-01","notes":"updated in browser","tags":["work"]}`
	updated := requestWithHeaders(s, "PUT", updatePath, updateBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-update-asset-4"})
	if updated.Code != 200 || !strings.Contains(updated.Body.String(), `"version":4`) {
		t.Fatalf("update web asset: %d %s", updated.Code, updated.Body)
	}
	stale := requestWithHeaders(s, "PUT", updatePath, updateBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-update-asset-stale"})
	if stale.Code != 409 || !strings.Contains(stale.Body.String(), `"remote_version":4`) {
		t.Fatalf("stale edit did not return a manual conflict: %d %s", stale.Code, stale.Body)
	}
	adminList := request(s, "POST", "/api/v1/admin/auth/login", `{"username":"operator","password":"first secure admin secret"}`)
	if adminList.Code != 200 {
		t.Fatalf("admin login: %d %s", adminList.Code, adminList.Body)
	}
	var adminToken struct {
		AccessToken string `json:"access_token"`
	}
	_ = json.Unmarshal(adminList.Body.Bytes(), &adminToken)
	adminAuth, _ := auth.NewService(repository.NewGormAuthRepository(db), []byte(testJWTSecret))
	userClaims, err := adminAuth.Authenticate(userToken.AccessToken)
	if err != nil {
		t.Fatal(err)
	}
	adminSees := requestBearer(s, "GET", "/api/v1/web/assets?owner_id="+userClaims.Subject, "", adminToken.AccessToken)
	if adminSees.Code != 200 || !strings.Contains(adminSees.Body.String(), asset.ID) {
		t.Fatalf("superadmin could not read selected account data: %d %s", adminSees.Code, adminSees.Body)
	}
	otherRegister := request(s, "POST", "/api/v1/auth/register", `{"username":"other-web-user","password":"another valid account password"}`)
	var otherToken struct {
		AccessToken string `json:"access_token"`
	}
	if otherRegister.Code != 201 || json.Unmarshal(otherRegister.Body.Bytes(), &otherToken) != nil {
		t.Fatalf("register second account: %d %s", otherRegister.Code, otherRegister.Body)
	}
	otherAccountRead := requestBearer(s, "GET", "/api/v1/web/assets?owner_id="+userClaims.Subject, "", otherToken.AccessToken)
	if otherAccountRead.Code != 403 {
		t.Fatalf("ordinary account read another account's assets: %d %s", otherAccountRead.Code, otherAccountRead.Body)
	}

	pull := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":0,"expiry_cursor":0,"idempotency_key":"web-to-android-pull","changes":[],"expiry_changes":[]}`, userToken.AccessToken)
	if pull.Code != 200 || !strings.Contains(pull.Body.String(), "Web laptop Mk2") {
		t.Fatalf("Android sync pull did not receive Web write: %d %s", pull.Code, pull.Body)
	}

	expiryBody := `{"name":"Coffee","category":"Food","package_expiry_date":"2026-11-01","opened_date":"","opened_validity_days":0,"location":"","notes":"","status":"IN_USE","archived_at":""}`
	expiry := requestWithHeaders(s, "POST", "/api/v1/web/expiry", expiryBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-create-expiry-1"})
	if expiry.Code != 201 {
		t.Fatalf("create expiry item: %d %s", expiry.Code, expiry.Body)
	}
	var item struct {
		ID      string `json:"id"`
		Version int64  `json:"version"`
	}
	if err := json.Unmarshal(expiry.Body.Bytes(), &item); err != nil || item.ID == "" || item.Version != 1 {
		t.Fatalf("created expiry version: %s err=%v", expiry.Body, err)
	}
	expiryPath := "/api/v1/web/expiry/" + item.ID
	expiryArchiveBody := `{"base_version":1,"name":"Coffee","category":"Food","package_expiry_date":"2026-11-01","opened_date":"","opened_validity_days":0,"location":"","notes":"","status":"IN_USE","archived_at":"2026-10-02T12:00:00Z"}`
	expiryArchive := requestWithHeaders(s, "PUT", expiryPath, expiryArchiveBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-archive-expiry-2"})
	if expiryArchive.Code != 200 || !strings.Contains(expiryArchive.Body.String(), `"version":2`) {
		t.Fatalf("archive web expiry item: %d %s", expiryArchive.Code, expiryArchive.Body)
	}
	expiryEditArchivedBody := `{"base_version":2,"name":"Coffee","category":"Food","package_expiry_date":"2026-11-01","opened_date":"","opened_validity_days":0,"location":"","notes":"changed while archived","status":"IN_USE","archived_at":"2026-10-02T12:00:00Z"}`
	expiryEditArchived := requestWithHeaders(s, "PUT", expiryPath, expiryEditArchivedBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-edit-archived-expiry"})
	if expiryEditArchived.Code != 409 || !strings.Contains(expiryEditArchived.Body.String(), "restore_before_edit") {
		t.Fatalf("archived expiry item was editable: %d %s", expiryEditArchived.Code, expiryEditArchived.Body)
	}
	expiryRestoreBody := `{"base_version":2,"name":"Coffee","category":"Food","package_expiry_date":"2026-11-01","opened_date":"","opened_validity_days":0,"location":"","notes":"","status":"IN_USE","archived_at":""}`
	expiryRestore := requestWithHeaders(s, "PUT", expiryPath, expiryRestoreBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-restore-expiry-3"})
	if expiryRestore.Code != 200 || !strings.Contains(expiryRestore.Body.String(), `"version":3`) {
		t.Fatalf("restore archived expiry item: %d %s", expiryRestore.Code, expiryRestore.Body)
	}
	expiryList := requestBearer(s, "GET", "/api/v1/web/expiry", "", userToken.AccessToken)
	if expiryList.Code != 200 || !strings.Contains(expiryList.Body.String(), item.ID) {
		t.Fatalf("list expiry items: %d %s", expiryList.Code, expiryList.Body)
	}
	expiryUpdateBody := `{"base_version":3,"name":"Coffee","category":"Food","package_expiry_date":"2026-11-01","opened_date":"2026-10-01","opened_validity_days":30,"location":"","notes":"opened in browser","status":"IN_USE","archived_at":""}`
	expiryUpdate := requestWithHeaders(s, "PUT", expiryPath, expiryUpdateBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-update-expiry-4"})
	if expiryUpdate.Code != 200 || !strings.Contains(expiryUpdate.Body.String(), `"version":4`) {
		t.Fatalf("update expiry item: %d %s", expiryUpdate.Code, expiryUpdate.Body)
	}
	expiryStale := requestWithHeaders(s, "PUT", expiryPath, expiryUpdateBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-update-expiry-stale"})
	if expiryStale.Code != 409 || !strings.Contains(expiryStale.Body.String(), `"remote_version":4`) {
		t.Fatalf("stale expiry edit did not return conflict: %d %s", expiryStale.Code, expiryStale.Body)
	}

	deleteBody := `{"base_version":4}`
	deleted := requestWithHeaders(s, "DELETE", updatePath, deleteBody, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-delete-asset-3"})
	if deleted.Code != 200 {
		t.Fatalf("delete web asset: %d %s", deleted.Code, deleted.Body)
	}
	missing := requestBearer(s, "GET", updatePath, "", userToken.AccessToken)
	if missing.Code != 404 {
		t.Fatalf("deleted asset remained in default Web reads: %d %s", missing.Code, missing.Body)
	}
	expiryDeleted := requestWithHeaders(s, "DELETE", expiryPath, `{"base_version":4}`, map[string]string{"Authorization": "Bearer " + userToken.AccessToken, "Idempotency-Key": "web-delete-expiry-5"})
	if expiryDeleted.Code != 200 {
		t.Fatalf("delete expiry item: %d %s", expiryDeleted.Code, expiryDeleted.Body)
	}
	deletedPull := requestBearer(s, "POST", "/api/v1/sync/assets", `{"cursor":1,"expiry_cursor":1,"idempotency_key":"web-delete-pull","changes":[],"expiry_changes":[]}`, userToken.AccessToken)
	if deletedPull.Code != 200 || !strings.Contains(deletedPull.Body.String(), "deleted_at") {
		t.Fatalf("delete tombstone was not available to Android sync: %d %s", deletedPull.Code, deletedPull.Body)
	}
}

func requestWithHeaders(s *Server, method, path, body string, headers map[string]string) *ut.ResponseRecorder {
	items := make([]ut.Header, 0, len(headers)+1)
	items = append(items, ut.Header{Key: "Content-Type", Value: "application/json"})
	for key, value := range headers {
		items = append(items, ut.Header{Key: key, Value: value})
	}
	return ut.PerformRequest(s.h.Engine, method, path, &ut.Body{Body: strings.NewReader(body), Len: len(body)}, items...)
}
