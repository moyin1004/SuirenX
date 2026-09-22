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
)

type Server struct{ h *server.Hertz }

type ServerOption func(*Server, *server.Hertz)

func WithM5(db *gorm.DB, jwtSecret string) ServerOption {
	return func(s *Server, h *server.Hertz) {
		authService, err := auth.NewService(repository.NewGormAuthRepository(db), []byte(jwtSecret))
		if err != nil {
			panic(err)
		}
		syncService := service.NewSyncService(repository.NewGormAssetRepository(db))
		h.Use(handlers.WithSyncActions(&m5Handlers{auth: authService, sync: syncService}))
		h.Use(withM5Auth(authService))
		_ = s
	}
}

func NewServer(address string, assets *service.AssetService, options ...ServerOption) *Server {
	h := server.Default(server.WithHostPorts(address))
	_ = assets // Kept in constructor for internal service-test compatibility.
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
