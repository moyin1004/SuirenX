package http

import (
	"strings"
	"testing"

	"github.com/moyin1004/suirenx/services/api/internal/service"
)

func TestWebClientAssetsAreEmbeddedWithSecurityHeaders(t *testing.T) {
	s := NewServer(":0", service.NewAssetService(nil), WithM5(openAuthTestDB(t), testJWTSecret))

	page := request(s, "GET", "/", "")
	if page.Code != 200 || !strings.Contains(page.Body.String(), "/web/app.js") || !strings.Contains(page.Body.String(), "/web/app.css") {
		t.Fatalf("web entry response: %d %s", page.Code, page.Body.String())
	}
	if got := page.Header().Get("Content-Security-Policy"); !strings.Contains(got, "script-src 'self'") || !strings.Contains(got, "frame-ancestors 'none'") {
		t.Fatalf("unexpected content security policy: %q", got)
	}
	if got := page.Header().Get("Cache-Control"); got != "no-store" {
		t.Fatalf("cache policy = %q, want no-store", got)
	}

	for _, asset := range []struct {
		path        string
		contentType string
	}{{"/web/app.js", "text/javascript; charset=utf-8"}, {"/web/app.css", "text/css; charset=utf-8"}} {
		response := request(s, "GET", asset.path, "")
		if response.Code != 200 || response.Header().Get("Content-Type") != asset.contentType || response.Body.Len() == 0 {
			t.Errorf("%s response: status=%d content-type=%q body=%d bytes", asset.path, response.Code, response.Header().Get("Content-Type"), response.Body.Len())
		}
	}
}
