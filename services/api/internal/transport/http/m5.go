package http

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	m5v1 "github.com/moyin1004/suirenx/services/api/biz/model/suirenx/m5/v1"
	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

type m5Handlers struct {
	auth              *auth.Service
	sync              *service.SyncService
	assets            *service.AssetService
	expiry            *service.ExpiryService
	admin             *service.AdminService
	config            *service.ConfigService
	adminBootstrapErr error
}

type authRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (m *m5Handlers) Register(c *app.RequestContext) {
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_account_request", "invalid account request")
		return
	}
	value, expiresAt, err := m.auth.Register(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusCreated, map[string]any{"access_token": value, "token_type": "Bearer", "expires_at": expiresAt.Format("2006-01-02T15:04:05Z07:00")})
}

func (m *m5Handlers) Login(c *app.RequestContext) {
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_account_request", "invalid account request")
		return
	}
	value, expiresAt, err := m.auth.Login(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"access_token": value, "token_type": "Bearer", "expires_at": expiresAt.Format("2006-01-02T15:04:05Z07:00")})
}

func (m *m5Handlers) AdminLogin(c *app.RequestContext) {
	if m.adminBootstrapErr != nil {
		writeAPIError(c, consts.StatusServiceUnavailable, "admin_login_unavailable", "superadmin configuration is missing or invalid; configure SUIRENX_ADMIN_USERNAME and SUIRENX_ADMIN_PASSWORD")
		return
	}
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_account_request", "invalid account request")
		return
	}
	value, expiresAt, err := m.auth.Login(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	claims, err := m.auth.Authenticate(value)
	if err != nil || claims.Role != auth.RoleSuperadmin {
		writeAPIError(c, consts.StatusForbidden, "superadmin_required", "superadmin account required")
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"access_token": value, "token_type": "Bearer", "expires_at": expiresAt.Format("2006-01-02T15:04:05Z07:00")})
}

func (m *m5Handlers) AdminListAccounts(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	accounts, err := m.admin.ListAccounts()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	response := make([]map[string]any, 0, len(accounts))
	for _, account := range accounts {
		disabledAt := ""
		if account.DisabledAt != nil {
			disabledAt = account.DisabledAt.UTC().Format("2006-01-02T15:04:05Z07:00")
		}
		response = append(response, map[string]any{"id": account.ID, "username": account.Username, "role": account.Role, "created_at": account.CreatedAt.UTC().Format("2006-01-02T15:04:05Z07:00"), "disabled_at": disabledAt})
	}
	c.JSON(consts.StatusOK, map[string]any{"accounts": response})
}

func (m *m5Handlers) AdminSetAccountStatus(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	bound, exists := c.Get("suirenx.boundRequest")
	request, ok := bound.(*m5v1.AdminAccountStatusRequest)
	if !exists || !ok || request.Disabled == nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_account_status", "disabled must be supplied")
		return
	}
	accountID := request.AccountId
	if err := m.admin.SetAccountDisabled(accountID, actorID, *request.Disabled); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"id": accountID, "disabled": *request.Disabled})
}

func (m *m5Handlers) AdminListConfigs(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	configs, err := m.config.ListConfigs()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	items := make([]map[string]any, 0, len(configs))
	for _, config := range configs {
		items = append(items, configMetadata(config))
	}
	c.JSON(consts.StatusOK, map[string]any{"configs": items})
}

func (m *m5Handlers) AdminCreateConfig(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.CreateConfigRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config", "invalid configuration request")
		return
	}
	config, err := m.config.CreateConfig(request.DisplayName, request.Content, actorID)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusCreated, configResponse(*config))
}

func (m *m5Handlers) AdminGetConfig(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.ConfigKeyRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config", "invalid configuration request")
		return
	}
	config, err := m.config.GetConfig(request.ConfigKey)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, configResponse(*config))
}

func (m *m5Handlers) AdminUpdateConfig(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.UpdateConfigRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config", "invalid configuration request")
		return
	}
	config, err := m.config.UpdateConfig(request.ConfigKey, request.DisplayName, request.Content, actorID)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, configResponse(*config))
}

func (m *m5Handlers) AdminDeleteConfig(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.ConfigKeyRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config", "invalid configuration request")
		return
	}
	if err := m.config.DeleteConfig(request.ConfigKey, actorID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
}

func (m *m5Handlers) AdminListApiTokens(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	tokens, err := m.config.ListApiTokens()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"tokens": tokens})
}

func (m *m5Handlers) AdminCreateApiToken(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.CreateApiTokenRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_api_token", "invalid API token request")
		return
	}
	issued, err := m.config.CreateApiToken(request.Label, request.TransportMode, request.ConfigKeys, request.ExpiresAt, actorID)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	response := tokenResponse(issued.Metadata)
	response["token"] = issued.Secret
	c.JSON(consts.StatusCreated, response)
}

func (m *m5Handlers) AdminRevokeApiToken(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.RevokeApiTokenRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_api_token", "invalid API token request")
		return
	}
	if err := m.config.RevokeApiToken(request.TokenId, actorID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
}

func (m *m5Handlers) AdminUpdateApiToken(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.UpdateApiTokenRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_api_token", "invalid API token update request")
		return
	}
	if err := m.config.UpdateApiToken(request.TokenId, request.Label, request.ConfigKeys, actorID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
}

func (m *m5Handlers) AdminGetConfigSizeLimit(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	limit, err := m.config.MaxConfigBytes()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]int64{"max_bytes": limit})
}

func (m *m5Handlers) AdminSetConfigSizeLimit(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.ConfigSizeLimitRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config_limit", "invalid configuration size limit")
		return
	}
	if err := m.config.SetMaxConfigBytes(request.MaxBytes, actorID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]int64{"max_bytes": request.MaxBytes})
}

func (m *m5Handlers) AdminGetAccountRegistrationSetting(c *app.RequestContext) {
	if _, err := m.superadminID(c); err != nil {
		writeM5Error(c, err)
		return
	}
	enabled, err := m.config.AccountRegistrationEnabled()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]bool{"enabled": enabled})
}

func (m *m5Handlers) AdminSetAccountRegistrationSetting(c *app.RequestContext) {
	actorID, err := m.superadminID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	request, ok := bound[m5v1.AccountRegistrationSettingRequest](c)
	if !ok || request.Enabled == nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_registration_setting", "enabled must be provided")
		return
	}
	if err := m.config.SetAccountRegistrationEnabled(*request.Enabled, actorID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]bool{"enabled": *request.Enabled})
}

func (m *m5Handlers) ReadConfigContent(c *app.RequestContext) {
	c.Response.Header.Set("Cache-Control", "no-store")
	request, ok := bound[m5v1.ConfigKeyRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_config_key", "invalid configuration key")
		return
	}
	bearer, query := bearerToken(c), string(c.Query("token"))
	content, err := m.config.ReadContent(request.ConfigKey, bearer, query)
	if err != nil {
		writeConfigReadError(c, err)
		return
	}
	c.Data(consts.StatusOK, "text/plain; charset=utf-8", []byte(content))
}

func (m *m5Handlers) ListWebAssets(c *app.RequestContext) {
	request, ok := bound[m5v1.WebAssetListRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid asset list request")
		return
	}
	ownerID, _, _, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	assets, err := m.assets.ForOwner(ownerID).ListScope(request.Status, request.Scope)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if assets == nil {
		assets = []service.AssetView{}
	}
	c.JSON(consts.StatusOK, map[string]any{"assets": assets})
}

func (m *m5Handlers) GetWebAsset(c *app.RequestContext) {
	request, ok := bound[m5v1.WebAssetGetRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid asset request")
		return
	}
	ownerID, _, _, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	asset, err := m.assets.ForOwner(ownerID).Get(request.Id)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, asset)
}

func (m *m5Handlers) CreateWebAsset(c *app.RequestContext) {
	request, ok := bound[m5v1.WebAssetCreateRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid asset request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	status := request.Status
	if status == "" {
		status = "ACTIVE"
	}
	id, err := newWebID()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), Changes: []service.SyncAssetInput{{
		ID: id, Name: request.Name, PriceCents: request.PriceCents, PurchaseDate: request.PurchaseDate,
		Status: status, ImageURL: request.ImageUrl, RetiredDate: request.RetiredDate, ArchivedAt: request.ArchivedAt,
		IconKey: request.IconKey, PurchaseChannel: request.PurchaseChannel, WarrantyEndDate: request.WarrantyEndDate,
		Notes: request.Notes, Tags: request.Tags,
	}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasAssetConflict(result) {
		writeAssetConflict(c, result.Conflicts[0])
		return
	}
	createdID := id
	if len(result.Applied) > 0 {
		createdID = result.Applied[0].Asset.ID
	}
	asset, err := m.assets.ForOwner(ownerID).Get(createdID)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "asset", createdID, "WEB_ASSET_CREATED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusCreated, asset)
}

func (m *m5Handlers) UpdateWebAsset(c *app.RequestContext) {
	request, ok := bound[m5v1.WebAssetUpdateRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid asset request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), Changes: []service.SyncAssetInput{{
		ID: request.Id, BaseVersion: request.BaseVersion, Name: request.Name, PriceCents: request.PriceCents,
		PurchaseDate: request.PurchaseDate, Status: request.Status, ImageURL: request.ImageUrl,
		RetiredDate: request.RetiredDate, ArchivedAt: request.ArchivedAt, IconKey: request.IconKey,
		PurchaseChannel: request.PurchaseChannel, WarrantyEndDate: request.WarrantyEndDate,
		Notes: request.Notes, Tags: request.Tags, RequireRestore: true,
	}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasAssetConflict(result) {
		writeAssetConflict(c, result.Conflicts[0])
		return
	}
	asset, err := m.assets.ForOwner(ownerID).Get(request.Id)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "asset", request.Id, "WEB_ASSET_UPDATED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusOK, asset)
}

func (m *m5Handlers) DeleteWebAsset(c *app.RequestContext) {
	request, ok := bound[m5v1.WebAssetDeleteRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid asset delete request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), Changes: []service.SyncAssetInput{{ID: request.Id, BaseVersion: request.BaseVersion, Deleted: true}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasAssetConflict(result) {
		writeAssetConflict(c, result.Conflicts[0])
		return
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "asset", request.Id, "WEB_ASSET_DELETED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "deleted", "id": request.Id})
}

func (m *m5Handlers) ListWebExpiry(c *app.RequestContext) {
	request, ok := bound[m5v1.WebExpiryListRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid expiry list request")
		return
	}
	ownerID, _, _, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	items, err := m.expiry.ListForOwner(ownerID)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result := make([]map[string]any, 0, len(items))
	for _, item := range items {
		result = append(result, webExpiry(item.Version, item.Item))
	}
	c.JSON(consts.StatusOK, map[string]any{"items": result})
}

func (m *m5Handlers) CreateWebExpiry(c *app.RequestContext) {
	request, ok := bound[m5v1.WebExpiryCreateRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid expiry request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	status := request.Status
	if status == "" {
		status = "IN_USE"
	}
	id, err := newWebID()
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), ExpiryChanges: []service.SyncExpiryInput{{
		ID: id, Name: request.Name, Category: request.Category, PackageExpiryDate: request.PackageExpiryDate,
		OpenedDate: request.OpenedDate, OpenedValidityDays: int(request.OpenedValidityDays), Location: request.Location,
		Notes: request.Notes, Status: status, ArchivedAt: request.ArchivedAt,
	}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasExpiryConflict(result) {
		writeExpiryConflict(c, result.ExpiryConflicts[0])
		return
	}
	created := domain.SyncExpiryItem{ID: id}
	version := int64(1)
	if len(result.AppliedExpiry) > 0 {
		created, version = result.AppliedExpiry[0].Item, result.AppliedExpiry[0].Version
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "expiry", created.ID, "WEB_EXPIRY_CREATED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusCreated, webExpiry(version, created))
}

func (m *m5Handlers) UpdateWebExpiry(c *app.RequestContext) {
	request, ok := bound[m5v1.WebExpiryUpdateRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid expiry request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), ExpiryChanges: []service.SyncExpiryInput{{
		ID: request.Id, BaseVersion: request.BaseVersion, Name: request.Name, Category: request.Category,
		PackageExpiryDate: request.PackageExpiryDate, OpenedDate: request.OpenedDate,
		OpenedValidityDays: int(request.OpenedValidityDays), Location: request.Location, Notes: request.Notes,
		Status: request.Status, ArchivedAt: request.ArchivedAt, RequireRestore: true,
	}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasExpiryConflict(result) {
		writeExpiryConflict(c, result.ExpiryConflicts[0])
		return
	}
	var updated domain.SyncExpiryItem
	var version int64
	if len(result.AppliedExpiry) > 0 {
		updated, version = result.AppliedExpiry[0].Item, result.AppliedExpiry[0].Version
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "expiry", request.Id, "WEB_EXPIRY_UPDATED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusOK, webExpiry(version, updated))
}

func (m *m5Handlers) DeleteWebExpiry(c *app.RequestContext) {
	request, ok := bound[m5v1.WebExpiryDeleteRequest](c)
	if !ok {
		writeAPIError(c, consts.StatusBadRequest, "invalid_web_request", "invalid expiry delete request")
		return
	}
	ownerID, actorID, adminMutation, err := m.webOwner(c, request.OwnerId)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	result, err := m.sync.Apply(ownerID, service.SyncBatchInput{IdempotencyKey: string(c.GetHeader("Idempotency-Key")), ExpiryChanges: []service.SyncExpiryInput{{ID: request.Id, BaseVersion: request.BaseVersion, Deleted: true}}})
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if hasExpiryConflict(result) {
		writeExpiryConflict(c, result.ExpiryConflicts[0])
		return
	}
	if adminMutation {
		if err := m.admin.RecordAudit(actorID, "expiry", request.Id, "WEB_EXPIRY_DELETED"); err != nil {
			writeM5Error(c, err)
			return
		}
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "deleted", "id": request.Id})
}

func (m *m5Handlers) webOwner(c *app.RequestContext, requestedOwner string) (ownerID, actorID string, adminMutation bool, err error) {
	actorID, err = m.ownerID(c)
	if err != nil {
		return
	}
	role, _ := c.Get(authRoleContextKey)
	if role == auth.RoleSuperadmin {
		ownerID = strings.TrimSpace(requestedOwner)
		if ownerID == "" {
			ownerID = actorID
		}
		adminMutation = ownerID != actorID
		return
	}
	if requestedOwner != "" && requestedOwner != actorID {
		err = auth.ErrSuperadminRequired
		return
	}
	ownerID = actorID
	return
}

func newWebID() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", fmt.Errorf("generate web record id: %w", err)
	}
	return hex.EncodeToString(raw[:]), nil
}

func hasAssetConflict(result domain.SyncBatchResult) bool  { return len(result.Conflicts) != 0 }
func hasExpiryConflict(result domain.SyncBatchResult) bool { return len(result.ExpiryConflicts) != 0 }

func writeAssetConflict(c *app.RequestContext, conflict domain.SyncConflict) {
	c.JSON(consts.StatusConflict, map[string]any{"error": "record changed on another device", "code": "version_conflict", "id": conflict.ID, "base_version": conflict.BaseVersion, "remote_version": conflict.RemoteVersion, "remote": conflict.Remote})
}

func writeExpiryConflict(c *app.RequestContext, conflict domain.SyncExpiryConflict) {
	c.JSON(consts.StatusConflict, map[string]any{"error": "record changed on another device", "code": "version_conflict", "id": conflict.ID, "base_version": conflict.BaseVersion, "remote_version": conflict.RemoteVersion, "remote": conflict.Remote})
}

func webExpiry(version int64, item domain.SyncExpiryItem) map[string]any {
	return map[string]any{"id": item.ID, "version": version, "name": item.Name, "category": item.Category, "package_expiry_date": item.PackageExpiryDate, "opened_date": item.OpenedDate, "opened_validity_days": item.OpenedValidityDays, "location": item.Location, "notes": item.Notes, "status": item.Status, "archived_at": item.ArchivedAt}
}

func bearerToken(c *app.RequestContext) string {
	parts := strings.Fields(strings.TrimSpace(string(c.GetHeader(consts.HeaderAuthorization))))
	if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
		return ""
	}
	return parts[1]
}

func bound[T any](c *app.RequestContext) (*T, bool) {
	value, exists := c.Get("suirenx.boundRequest")
	request, ok := value.(*T)
	return request, exists && ok
}

func configMetadata(config domain.ConfigFile) map[string]any {
	return map[string]any{"config_key": config.Key, "display_name": config.DisplayName, "created_at": formatAPITime(config.CreatedAt), "updated_at": formatAPITime(config.UpdatedAt), "deleted_at": ""}
}

func configResponse(config domain.ConfigFile) map[string]any {
	response := configMetadata(config)
	response["content"] = config.Content
	return response
}

func tokenResponse(token domain.ApiToken) map[string]any {
	return map[string]any{"id": token.ID, "label": token.Label, "transport_mode": token.TransportMode, "config_keys": token.ConfigKeys, "created_at": formatAPITime(token.CreatedAt), "expires_at": formatOptionalAPITime(token.ExpiresAt), "revoked_at": formatOptionalAPITime(token.RevokedAt), "last_used_at": formatOptionalAPITime(token.LastUsedAt)}
}

func formatAPITime(value time.Time) string { return value.UTC().Format(time.RFC3339) }
func formatOptionalAPITime(value *time.Time) string {
	if value == nil {
		return ""
	}
	return formatAPITime(*value)
}

func (m *m5Handlers) Logout(c *app.RequestContext) {
	_, err := m.ownerID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
}

func (m *m5Handlers) SyncAssets(c *app.RequestContext) {
	ownerID, err := m.ownerID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	var request service.SyncBatchInput
	if err := c.BindAndValidate(&request); err != nil {
		writeAPIError(c, consts.StatusBadRequest, "invalid_sync_request", "invalid sync request")
		return
	}
	result, err := m.sync.Apply(ownerID, request)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, result)
}

func (m *m5Handlers) ownerID(c *app.RequestContext) (string, error) {
	value, exists := c.Get(authOwnerIDContextKey)
	ownerID, ok := value.(string)
	if !exists || !ok || ownerID == "" {
		return "", auth.ErrInvalidToken
	}
	return ownerID, nil
}

func (m *m5Handlers) superadminID(c *app.RequestContext) (string, error) {
	ownerID, err := m.ownerID(c)
	if err != nil {
		return "", err
	}
	role, _ := c.Get(authRoleContextKey)
	if role != auth.RoleSuperadmin {
		return "", auth.ErrSuperadminRequired
	}
	return ownerID, nil
}

func writeM5Error(c *app.RequestContext, err error) {
	status := consts.StatusInternalServerError
	code := "internal_server_error"
	switch {
	case errors.Is(err, auth.ErrInvalidCredentials), errors.Is(err, auth.ErrInvalidToken):
		status = consts.StatusUnauthorized
		code = "invalid_credentials"
	case errors.Is(err, auth.ErrAccountDisabled):
		status = consts.StatusUnauthorized
		code = "account_disabled"
	case errors.Is(err, auth.ErrSuperadminRequired):
		status = consts.StatusForbidden
		code = "superadmin_required"
	case errors.Is(err, auth.ErrRegistrationDisabled):
		status = consts.StatusForbidden
		code = "registration_closed"
	case errors.Is(err, auth.ErrInvalidAccount), errors.Is(err, auth.ErrUsernameTaken), errors.Is(err, service.ErrInvalidSyncRequest):
		status = consts.StatusBadRequest
		code = "invalid_request"
	case errors.Is(err, service.ErrSyncConflict):
		status = consts.StatusConflict
		code = "sync_conflict"
	case errors.Is(err, service.ErrArchivedRecord):
		status = consts.StatusConflict
		code = "restore_before_edit"
	case errors.Is(err, repository.ErrLastSuperadmin):
		status = consts.StatusConflict
		code = "last_superadmin"
	case errors.Is(err, service.ErrInvalidAdminInput):
		status = consts.StatusBadRequest
		code = "invalid_admin_input"
	case errors.Is(err, repository.ErrNotFound):
		status = consts.StatusNotFound
		code = "not_found"
	case errors.Is(err, service.ErrAssetNotFound):
		status = consts.StatusNotFound
		code = "asset_not_found"
	case errors.Is(err, service.ErrInvalidStatus), errors.Is(err, service.ErrInvalidScope), errors.Is(err, service.ErrInvalidName), errors.Is(err, service.ErrInvalidPrice), errors.Is(err, service.ErrInvalidPurchaseDate), errors.Is(err, service.ErrInvalidRetiredDate), errors.Is(err, service.ErrInvalidAssetMetadata):
		status = consts.StatusBadRequest
		code = "invalid_asset"
	case errors.Is(err, repository.ErrConfigNotFound):
		status = consts.StatusNotFound
		code = "config_not_found"
	case errors.Is(err, repository.ErrConfigNameTaken):
		status = consts.StatusConflict
		code = "config_name_taken"
	case errors.Is(err, repository.ErrConfigTooLarge):
		status = consts.StatusRequestEntityTooLarge
		code = "config_too_large"
	case errors.Is(err, repository.ErrInvalidConfig), errors.Is(err, service.ErrInvalidConfigName):
		status = consts.StatusBadRequest
		code = "invalid_config"
	case errors.Is(err, repository.ErrInvalidApiToken):
		status = consts.StatusBadRequest
		code = "invalid_api_token"
	case errors.Is(err, repository.ErrApiTokenNotFound):
		status = consts.StatusNotFound
		code = "api_token_not_found"
	case errors.Is(err, repository.ErrApiTokenRevoked):
		status = consts.StatusConflict
		code = "api_token_revoked"
	case errors.Is(err, repository.ErrInvalidConfigLimit):
		status = consts.StatusBadRequest
		code = "invalid_config_limit"
	case errors.Is(err, repository.ErrApiTokenInvalid), errors.Is(err, repository.ErrApiTokenExpired):
		status = consts.StatusUnauthorized
		code = "invalid_api_token"
	case errors.Is(err, repository.ErrApiTokenScope):
		status = consts.StatusForbidden
		code = "token_scope_denied"
	case errors.Is(err, service.ErrConfigRateLimited):
		status = consts.StatusTooManyRequests
		code = "rate_limited"
	}
	message := err.Error()
	if status == consts.StatusInternalServerError {
		message = "internal server error"
	}
	writeAPIError(c, status, code, message)
}

func writeConfigReadError(c *app.RequestContext, err error) {
	status := consts.StatusInternalServerError
	code, message := "internal_server_error", "internal server error"
	switch {
	case errors.Is(err, repository.ErrApiTokenInvalid):
		status, code, message = consts.StatusUnauthorized, "invalid_api_token", "API token is invalid"
	case errors.Is(err, repository.ErrApiTokenExpired):
		status, code, message = consts.StatusUnauthorized, "api_token_expired", "API token has expired"
	case errors.Is(err, repository.ErrApiTokenRevoked):
		status, code, message = consts.StatusUnauthorized, "api_token_revoked", "API token has been revoked"
	case errors.Is(err, repository.ErrApiTokenScope):
		status, code, message = consts.StatusForbidden, "token_scope_denied", "API token is not authorized for this configuration"
	case errors.Is(err, repository.ErrConfigNotFound):
		status, code, message = consts.StatusNotFound, "config_not_found", "configuration not found"
	case errors.Is(err, service.ErrConfigRateLimited):
		status, code, message = consts.StatusTooManyRequests, "rate_limited", "request rate limit exceeded"
	}
	writeAPIError(c, status, code, message)
}
