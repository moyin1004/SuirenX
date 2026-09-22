package http

import (
	"errors"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

type m5Handlers struct {
	auth *auth.Service
	sync *service.SyncService
}

type authRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (m *m5Handlers) Register(c *app.RequestContext) {
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		c.JSON(consts.StatusBadRequest, map[string]string{"error": "invalid account request"})
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
		c.JSON(consts.StatusBadRequest, map[string]string{"error": "invalid account request"})
		return
	}
	value, expiresAt, err := m.auth.Login(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"access_token": value, "token_type": "Bearer", "expires_at": expiresAt.Format("2006-01-02T15:04:05Z07:00")})
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
		c.JSON(consts.StatusBadRequest, map[string]string{"error": "invalid sync request"})
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

func writeM5Error(c *app.RequestContext, err error) {
	status := consts.StatusInternalServerError
	switch {
	case errors.Is(err, auth.ErrInvalidCredentials), errors.Is(err, auth.ErrInvalidToken):
		status = consts.StatusUnauthorized
	case errors.Is(err, auth.ErrInvalidAccount), errors.Is(err, auth.ErrUsernameTaken), errors.Is(err, service.ErrInvalidSyncRequest):
		status = consts.StatusBadRequest
	case errors.Is(err, service.ErrSyncConflict):
		status = consts.StatusConflict
	}
	message := err.Error()
	if status == consts.StatusInternalServerError {
		message = "internal server error"
	}
	c.JSON(status, map[string]string{"error": message})
}
