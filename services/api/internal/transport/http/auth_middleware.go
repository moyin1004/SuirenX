package http

import (
	"context"
	"strings"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	"github.com/moyin1004/suirenx/services/api/internal/auth"
)

const authOwnerIDContextKey = "suirenx.auth.owner_id"
const authRoleContextKey = "suirenx.auth.role"

// withM5Auth only installs verification on endpoints that require an account.
// Health, login and register are intentionally allowed through without this
// middleware; they must remain usable before an account has a JWT.
func withM5Auth(authService *auth.Service) app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		if !requiresM5Auth(c) {
			c.Next(ctx)
			return
		}

		value := strings.TrimSpace(string(c.GetHeader(consts.HeaderAuthorization)))
		parts := strings.Fields(value)
		if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
			c.Abort()
			writeAPIError(c, consts.StatusUnauthorized, "invalid_token", auth.ErrInvalidToken.Error())
			return
		}
		claims, err := authService.Authenticate(parts[1])
		if err != nil {
			c.Abort()
			writeAPIError(c, consts.StatusUnauthorized, "invalid_token", auth.ErrInvalidToken.Error())
			return
		}
		c.Set(authOwnerIDContextKey, claims.Subject)
		c.Set(authRoleContextKey, claims.Role)
		c.Next(ctx)
	}
}

func requiresM5Auth(c *app.RequestContext) bool {
	switch string(c.Path()) {
	case "/api/v1/auth/logout", "/api/v1/sync/assets":
		return true
	default:
		if string(c.Path()) == "/api/v1/admin/auth/login" {
			return false
		}
		if strings.HasPrefix(string(c.Path()), "/api/v1/configs/") {
			return false // Config reads use the separately-scoped API token guard.
		}
		return strings.HasPrefix(string(c.Path()), "/api/v1/web/") || strings.HasPrefix(string(c.Path()), "/api/v1/admin/") || strings.HasPrefix(string(c.Path()), "/api/v1/configs/")
	}
}
