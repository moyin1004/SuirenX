package http

import (
	"errors"
	"strings"

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

func (m *m5Handlers) register(c *app.RequestContext) {
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		c.JSON(consts.StatusBadRequest, map[string]string{"error": "invalid account request"})
		return
	}
	token, err := m.auth.Register(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusCreated, map[string]any{"access_token": token.Value, "token_type": "Bearer", "expires_at": token.ExpiresAt.Format("2006-01-02T15:04:05Z07:00")})
}

func (m *m5Handlers) login(c *app.RequestContext) {
	var request authRequest
	if err := c.BindAndValidate(&request); err != nil {
		c.JSON(consts.StatusBadRequest, map[string]string{"error": "invalid account request"})
		return
	}
	token, err := m.auth.Login(request.Username, request.Password)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]any{"access_token": token.Value, "token_type": "Bearer", "expires_at": token.ExpiresAt.Format("2006-01-02T15:04:05Z07:00")})
}

func (m *m5Handlers) logout(c *app.RequestContext) {
	ownerID, err := m.ownerID(c)
	if err != nil {
		writeM5Error(c, err)
		return
	}
	if err := m.auth.Logout(ownerID); err != nil {
		writeM5Error(c, err)
		return
	}
	c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
}

func (m *m5Handlers) syncAssets(c *app.RequestContext) {
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
	value := strings.TrimSpace(string(c.GetHeader(consts.HeaderAuthorization)))
	parts := strings.Fields(value)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
		return "", auth.ErrInvalidToken
	}
	return m.auth.Authenticate(parts[1])
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
