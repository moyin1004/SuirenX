package http

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	nethttp "net/http"
	"strings"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/moyin1004/suirenx/services/api/biz/router"
	"github.com/moyin1004/suirenx/services/api/internal/domain"
)

func TestConfigContentBoundariesOverHTTP(t *testing.T) {
	t.Setenv("SUIRENX_ADMIN_USERNAME", "operator")
	t.Setenv("SUIRENX_ADMIN_PASSWORD", "first secure admin secret")
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	// Use the production-configured limit with a pre-bound listener. Unlike
	// ut.PerformRequest, this exercises Content-Length checks and JSON on the wire.
	limit := NewServer(":0", nil).h.GetOptions().MaxRequestBodySize
	h := server.Default(server.WithListener(listener), server.WithMaxRequestBodySize(limit))
	s := &Server{h: h}
	WithM5(openAuthTestDB(t), testJWTSecret)(s, h)
	router.GeneratedRegister(h)
	token := adminAccessToken(t, s)
	setting := requestBearer(s, "PUT", "/api/v1/admin/settings/config-max-bytes",
		fmt.Sprintf(`{"max_bytes":%d}`, domain.MaxConfigContentBytes), token)
	if setting.Code != 200 {
		t.Fatalf("set content limit: %d", setting.Code)
	}
	done := make(chan error, 1)
	go func() { done <- h.Run() }()
	t.Cleanup(func() {
		_ = h.Close()
		select {
		case err := <-done:
			if err != nil {
				t.Errorf("HTTP server stopped: %v", err)
			}
		case <-time.After(5 * time.Second):
			t.Error("HTTP server did not stop")
		}
	})
	client := &nethttp.Client{Timeout: 30 * time.Second}
	t.Cleanup(client.CloseIdleConnections)
	configKey := ""
	for _, test := range []struct {
		name, method, character string
		length, status          int
	}{
		{"quotes_at_cap", "POST", `"`, int(domain.MaxConfigContentBytes), 201},
		{"sixfold_escape_at_cap", "PUT", "\x00", int(domain.MaxConfigContentBytes), 200},
		{"decoded_content_over_cap", "PUT", "x", int(domain.MaxConfigContentBytes) + 1, 413},
	} {
		if !t.Run(test.name, func(t *testing.T) {
			content := strings.Repeat(test.character, test.length)
			body, err := json.Marshal(map[string]string{"display_name": test.name, "content": content})
			if err != nil {
				t.Fatal(err)
			}
			path := "/api/v1/admin/configs"
			if test.method == "PUT" {
				path += "/" + configKey
			}
			request, err := nethttp.NewRequest(test.method, "http://"+listener.Addr().String()+path, bytes.NewReader(body))
			if err != nil {
				t.Fatal(err)
			}
			request.Header.Set("Authorization", "Bearer "+token)
			request.Header.Set("Content-Type", "application/json")
			response, err := client.Do(request)
			if err != nil {
				t.Fatalf("send %d decoded / %d encoded bytes: %v", len(content), len(body), err)
			}
			defer response.Body.Close()
			if response.StatusCode != test.status {
				t.Fatalf("%d decoded / %d encoded bytes: status %d, want %d", len(content), len(body), response.StatusCode, test.status)
			}
			var result struct {
				Key     string `json:"config_key"`
				Content string `json:"content"`
				Code    string `json:"code"`
			}
			if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
				t.Fatal(err)
			}
			if test.status == 413 {
				if result.Code != "config_too_large" {
					t.Fatalf("expected service content-size rejection, got %q", result.Code)
				}
			} else {
				if result.Content != content || result.Key == "" {
					t.Fatal("configuration content was not preserved")
				}
				configKey = result.Key
			}
			t.Logf("decoded=%d encoded=%d status=%d", len(content), len(body), response.StatusCode)
		}) {
			return
		}
	}
	// A declared request above the wire cap is rejected before buffering a body.
	connection, err := net.DialTimeout("tcp", listener.Addr().String(), 5*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	if err := connection.SetDeadline(time.Now().Add(5 * time.Second)); err != nil {
		t.Fatal(err)
	}
	if _, err := fmt.Fprintf(connection, "POST /api/v1/admin/configs HTTP/1.1\r\nHost: localhost\r\nContent-Length: %d\r\nConnection: close\r\n\r\n", limit+1); err != nil {
		t.Fatal(err)
	}
	response, err := nethttp.ReadResponse(bufio.NewReader(connection), nil)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 413 {
		t.Fatalf("request above wire cap: status %d, want 413", response.StatusCode)
	}
}
