package http

import (
	"context"
	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	handlers "github.com/moyin1004/suirenx/services/api/biz/handler/v1"
	"github.com/moyin1004/suirenx/services/api/biz/router"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

type Server struct{ h *server.Hertz }

func NewServer(address string, assets *service.AssetService) *Server {
	h := server.Default(server.WithHostPorts(address))
	h.Use(handlers.WithAssets(assets))
	h.GET("/healthz", func(_ context.Context, c *app.RequestContext) {
		c.JSON(consts.StatusOK, map[string]string{"status": "ok"})
	})
	router.GeneratedRegister(h)
	return &Server{h: h}
}

func (s *Server) Run() { s.h.Spin() }
