package http

import (
	"context"
	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	handlers "github.com/moyin1004/suirenx/services/api/biz/handler/v1"
	"github.com/moyin1004/suirenx/services/api/biz/router"
	"github.com/moyin1004/suirenx/services/api/internal/auth"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
	"github.com/moyin1004/suirenx/services/api/internal/service"
	"gorm.io/gorm"
	"strings"
)

type Server struct{ h *server.Hertz }

type ServerOption func(*Server, *server.Hertz)

func WithM5(db *gorm.DB) ServerOption {
	return func(s *Server, h *server.Hertz) {
		authService := auth.NewService(repository.NewGormAuthRepository(db))
		syncService := service.NewSyncService(repository.NewGormAssetRepository(db))
		handlers := &m5Handlers{auth: authService, sync: syncService}
		h.Use(func(ctx context.Context, c *app.RequestContext) {
			if strings.HasPrefix(string(c.Path()), "/api/v1/assets") {
				value := strings.Fields(strings.TrimSpace(string(c.GetHeader(consts.HeaderAuthorization))))
				if len(value) != 2 || !strings.EqualFold(value[0], "bearer") {
					c.AbortWithStatusJSON(consts.StatusUnauthorized, map[string]string{"error": "invalid or expired bearer token"})
					return
				}
				ownerID, err := authService.Authenticate(value[1])
				if err != nil {
					c.AbortWithStatusJSON(consts.StatusUnauthorized, map[string]string{"error": "invalid or expired bearer token"})
					return
				}
				c.Set("suirenx.ownerID", ownerID)
			}
			c.Next(ctx)
		})
		h.POST("/api/v1/auth/register", func(_ context.Context, c *app.RequestContext) { handlers.register(c) })
		h.POST("/api/v1/auth/login", func(_ context.Context, c *app.RequestContext) { handlers.login(c) })
		h.POST("/api/v1/auth/logout", func(_ context.Context, c *app.RequestContext) { handlers.logout(c) })
		h.POST("/api/v1/sync/assets", func(_ context.Context, c *app.RequestContext) { handlers.syncAssets(c) })
		_ = s
	}
}

func NewServer(address string, assets *service.AssetService, options ...ServerOption) *Server {
	h := server.Default(server.WithHostPorts(address))
	h.Use(handlers.WithAssets(assets))
	s := &Server{h: h}
	for _, option := range options {
		option(s, h)
	}
	h.GET("/healthz", func(_ context.Context, c *app.RequestContext) {
		c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
	})
	router.GeneratedRegister(h)
	return s
}

func (s *Server) Run() { s.h.Spin() }
