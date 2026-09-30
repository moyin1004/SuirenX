package v1

import (
	"context"
	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
)

type SyncActions interface {
	Register(*app.RequestContext)
	Login(*app.RequestContext)
	Logout(*app.RequestContext)
	SyncAssets(*app.RequestContext)
}

type requestError struct {
	Error     string `json:"error"`
	Code      string `json:"code"`
	RequestID string `json:"request_id"`
}

func WithSyncActions(actions SyncActions) app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		c.Set("suirenx.syncActions", actions)
		c.Next(ctx)
	}
}

func actions(c *app.RequestContext) SyncActions {
	value, _ := c.Get("suirenx.syncActions")
	return value.(SyncActions)
}

// Validate the generated transport contract before delegating to the service adapter.
func dispatch[T any](c *app.RequestContext, action func(*app.RequestContext)) {
	var request T
	if err := c.BindAndValidate(&request); err != nil {
		c.JSON(consts.StatusBadRequest, requestError{
			Error: "invalid request", Code: "invalid_request", RequestID: string(c.GetHeader("X-Request-ID")),
		})
		return
	}
	action(c)
}
