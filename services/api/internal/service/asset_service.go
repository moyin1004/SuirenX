package service

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

var (
	ErrInvalidName         = errors.New("asset name is required")
	ErrInvalidPrice        = errors.New("price must not be negative")
	ErrInvalidPurchaseDate = errors.New("purchase date must use YYYY-MM-DD")
	ErrInvalidStatus       = errors.New("invalid asset status")
)

type AssetView struct {
	ID             string `json:"id"`
	Name           string `json:"name"`
	PriceCents     int64  `json:"price_cents"`
	PurchaseDate   string `json:"purchase_date"`
	Status         string `json:"status"`
	ImageURL       string `json:"image_url"`
	HeldDays       int    `json:"held_days"`
	DailyCostCents int64  `json:"daily_cost_cents"`
	CreatedAt      string `json:"created_at"`
	UpdatedAt      string `json:"updated_at"`
}

type CreateAssetInput struct {
	Name         string `json:"name"`
	PriceCents   int64  `json:"price_cents"`
	PurchaseDate string `json:"purchase_date"`
	ImageURL     string `json:"image_url"`
}

type AssetService struct {
	repository repository.AssetRepository
	now        func() time.Time
}

func NewAssetService(repository repository.AssetRepository) *AssetService {
	return &AssetService{repository: repository, now: time.Now}
}

func (s *AssetService) List(status string) ([]AssetView, error) {
	var filter *domain.AssetStatus
	if status != "" {
		parsed := domain.AssetStatus(strings.ToUpper(status))
		if !parsed.Valid() {
			return nil, ErrInvalidStatus
		}
		filter = &parsed
	}

	assets, err := s.repository.List(filter)
	if err != nil {
		return nil, fmt.Errorf("list assets: %w", err)
	}
	views := make([]AssetView, 0, len(assets))
	for _, asset := range assets {
		views = append(views, s.toView(asset))
	}
	return views, nil
}

func (s *AssetService) Create(input CreateAssetInput) (AssetView, error) {
	name := strings.TrimSpace(input.Name)
	if name == "" {
		return AssetView{}, ErrInvalidName
	}
	if input.PriceCents < 0 {
		return AssetView{}, ErrInvalidPrice
	}
	purchaseDate, err := time.Parse(time.DateOnly, input.PurchaseDate)
	if err != nil {
		return AssetView{}, ErrInvalidPurchaseDate
	}

	asset := domain.Asset{
		ID:           newID(),
		Name:         name,
		PriceCents:   input.PriceCents,
		PurchaseDate: purchaseDate,
		Status:       domain.AssetStatusActive,
		ImageURL:     strings.TrimSpace(input.ImageURL),
	}
	if err := s.repository.Create(&asset); err != nil {
		return AssetView{}, fmt.Errorf("create asset: %w", err)
	}
	return s.toView(asset), nil
}

func (s *AssetService) SeedExamples() error {
	count, err := s.repository.Count()
	if err != nil || count > 0 {
		return err
	}

	examples := []CreateAssetInput{
		{Name: "MacBook Pro", PriceCents: 1699900, PurchaseDate: s.now().AddDate(0, 0, -132).Format(time.DateOnly)},
		{Name: "荣耀 Magic8 Pro", PriceCents: 659900, PurchaseDate: s.now().AddDate(0, 0, -175).Format(time.DateOnly)},
	}
	for _, input := range examples {
		if _, err := s.Create(input); err != nil {
			return err
		}
	}
	return nil
}

func (s *AssetService) toView(asset domain.Asset) AssetView {
	end := s.now()
	if asset.RetiredAt != nil {
		end = *asset.RetiredAt
	}
	endDate := time.Date(end.Year(), end.Month(), end.Day(), 0, 0, 0, 0, time.UTC)
	days := int(endDate.Sub(asset.PurchaseDate).Hours()/24) + 1
	if days < 1 {
		days = 1
	}
	// Round to the nearest cent without adding to price and overflowing int64.
	dailyCost := asset.PriceCents / int64(days)
	if asset.PriceCents%int64(days) >= (int64(days)+1)/2 {
		dailyCost++
	}

	return AssetView{
		ID:             asset.ID,
		Name:           asset.Name,
		PriceCents:     asset.PriceCents,
		PurchaseDate:   asset.PurchaseDate.Format(time.DateOnly),
		Status:         string(asset.Status),
		ImageURL:       asset.ImageURL,
		HeldDays:       days,
		DailyCostCents: dailyCost,
		CreatedAt:      asset.CreatedAt.Format(time.RFC3339),
		UpdatedAt:      asset.UpdatedAt.Format(time.RFC3339),
	}
}

func newID() string {
	var value [16]byte
	if _, err := rand.Read(value[:]); err != nil {
		panic(fmt.Sprintf("generate asset id: %v", err))
	}
	return hex.EncodeToString(value[:])
}
