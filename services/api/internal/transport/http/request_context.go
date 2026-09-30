package http

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"log/slog"
	"regexp"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
)

const requestIDHeader = "X-Request-ID"

var safeRequestID = regexp.MustCompile(`^[A-Za-z0-9._-]{1,64}$`)

type errorResponse struct {
	Error     string `json:"error"`
	Code      string `json:"code"`
	RequestID string `json:"request_id"`
}

func withRequestContext() app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		requestID := string(c.GetHeader(requestIDHeader))
		if !safeRequestID.MatchString(requestID) {
			requestID = newRequestID()
		}
		c.Set(requestIDHeader, requestID)
		c.Response.Header.Set(requestIDHeader, requestID)
		started := time.Now()
		c.Next(ctx)
		slog.Info("http request",
			"request_id", requestID,
			"method", string(c.Method()),
			"path", string(c.Path()),
			"status", c.Response.StatusCode(),
			"duration_ms", time.Since(started).Milliseconds(),
		)
	}
}

func newRequestID() string {
	var value [16]byte
	if _, err := rand.Read(value[:]); err != nil {
		// The fallback is still bounded and safe for a response header/log field.
		return "unavailable"
	}
	return hex.EncodeToString(value[:])
}

func writeAPIError(c *app.RequestContext, status int, code, message string) {
	requestID, _ := c.Get(requestIDHeader)
	id, _ := requestID.(string)
	c.JSON(status, errorResponse{Error: message, Code: code, RequestID: id})
}
