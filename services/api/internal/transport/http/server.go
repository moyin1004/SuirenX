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
	"github.com/moyin1004/suirenx/services/api/internal/transport/http/webui"
	"gorm.io/gorm"
	"os"
	"time"
)

const (
	httpReadTimeout  = 10 * time.Second
	httpWriteTimeout = 30 * time.Second
	httpIdleTimeout  = 60 * time.Second
)

type Server struct{ h *server.Hertz }

type ServerOption func(*Server, *server.Hertz)

func WithM5(db *gorm.DB, jwtSecret string) ServerOption {
	return func(s *Server, h *server.Hertz) {
		authService, err := auth.NewService(repository.NewGormAuthRepository(db), []byte(jwtSecret))
		if err != nil {
			panic(err)
		}
		adminUsername, adminPassword := os.Getenv("SUIRENX_ADMIN_USERNAME"), os.Getenv("SUIRENX_ADMIN_PASSWORD")
		var adminBootstrapErr error
		if adminUsername == "" || adminPassword == "" {
			adminBootstrapErr = auth.ErrAdminBootstrap
		} else if err := authService.BootstrapSuperadmin(adminUsername, adminPassword); err != nil {
			adminBootstrapErr = err
		}
		assetRepository := repository.NewGormAssetRepository(db)
		syncService := service.NewSyncService(assetRepository)
		configRepository := repository.NewGormAuthRepository(db)
		h.Use(handlers.WithSyncActions(&m5Handlers{auth: authService, sync: syncService, assets: service.NewAssetService(assetRepository), expiry: service.NewExpiryService(assetRepository), admin: service.NewAdminService(configRepository), config: service.NewConfigService(configRepository), adminBootstrapErr: adminBootstrapErr}))
		h.Use(withM5Auth(authService))
		_ = s
	}
}

func NewServer(address string, assets *service.AssetService, options ...ServerOption) *Server {
	h := server.Default(
		server.WithHostPorts(address),
		server.WithReadTimeout(httpReadTimeout),
		server.WithWriteTimeout(httpWriteTimeout),
		server.WithIdleTimeout(httpIdleTimeout),
	)
	_ = assets // Kept in constructor for internal service-test compatibility.
	s := &Server{h: h}
	h.Use(withRequestContext())
	for _, option := range options {
		option(s, h)
	}
	h.GET("/healthz", func(_ context.Context, c *app.RequestContext) {
		c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
	})
	router.GeneratedRegister(h)
	webui.Register(h)
	return s
}

func (s *Server) Run() { s.h.Spin() }
