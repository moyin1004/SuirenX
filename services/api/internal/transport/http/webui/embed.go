package webui

import (
	"bytes"
	"context"
	"embed"
	"html/template"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	"github.com/moyin1004/suirenx"
)

//go:embed index.html app.css app.js
var files embed.FS

var indexTemplate = template.Must(template.ParseFS(files, "index.html"))

// Register serves the same-origin Web client from the API binary.
func Register(h *server.Hertz) {
	h.GET("/", serve("index.html", "text/html; charset=utf-8"))
	h.GET("/web/app.css", serve("app.css", "text/css; charset=utf-8"))
	h.GET("/web/app.js", serve("app.js", "text/javascript; charset=utf-8"))
}

func serve(path, contentType string) app.HandlerFunc {
	return func(_ context.Context, c *app.RequestContext) {
		body, err := files.ReadFile(path)
		if err != nil {
			c.AbortWithStatus(consts.StatusNotFound)
			return
		}
		if path == "index.html" {
			var rendered bytes.Buffer
			if err := indexTemplate.Execute(&rendered, struct{ Version string }{suirenx.ProductVersion()}); err != nil {
				c.AbortWithStatus(consts.StatusInternalServerError)
				return
			}
			body = rendered.Bytes()
		}
		c.Header("Cache-Control", "no-store")
		c.Header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'")
		c.Header("X-Content-Type-Options", "nosniff")
		c.Data(consts.StatusOK, contentType, body)
	}
}
