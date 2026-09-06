package v1

import (
	"context"
	"errors"
	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	model "github.com/moyin1004/suirenx/services/api/biz/model/suirenx/asset/v1"
	"github.com/moyin1004/suirenx/services/api/internal/service"
)

const assetServiceKey = "suirenx.assetService"

// WithAssets supplies a server-scoped service to the generated function handlers.
// No global service state is shared between servers or tests.
func WithAssets(assets *service.AssetService) app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		c.Set(assetServiceKey, assets)
		c.Next(ctx)
	}
}

func assetService(c *app.RequestContext) *service.AssetService {
	value, _ := c.Get(assetServiceKey)
	return value.(*service.AssetService)
}

func toAPIAsset(asset service.AssetView) *model.Asset {
	return &model.Asset{
		Id: asset.ID, Name: asset.Name, PriceCents: asset.PriceCents,
		PurchaseDate: asset.PurchaseDate, Status: asset.Status, ImageUrl: asset.ImageURL,
		HeldDays: int32(asset.HeldDays), DailyCostCents: asset.DailyCostCents,
		CreatedAt: asset.CreatedAt, UpdatedAt: asset.UpdatedAt, RetiredDate: asset.RetiredDate, ArchivedAt: asset.ArchivedAt,
	}
}

func writeError(c *app.RequestContext, err error) {
	status := consts.StatusInternalServerError
	if errors.Is(err, service.ErrInvalidScope) || errors.Is(err, service.ErrInvalidArchiveAction) || errors.Is(err, service.ErrInvalidName) || errors.Is(err, service.ErrInvalidPrice) ||
		errors.Is(err, service.ErrInvalidRetiredDate) || errors.Is(err, service.ErrInvalidPurchaseDate) || errors.Is(err, service.ErrInvalidStatus) {
		status = consts.StatusBadRequest
	} else if errors.Is(err, service.ErrAssetArchived) {
		status = consts.StatusConflict
	} else if errors.Is(err, service.ErrAssetNotFound) {
		status = consts.StatusNotFound
	}
	message := err.Error()
	if status == consts.StatusInternalServerError {
		message = "internal server error"
	}
	c.JSON(status, map[string]string{"error": message})
}
