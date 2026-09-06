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
	ErrInvalidIcon          = errors.New("unsupported asset icon key")
	ErrInvalidName          = errors.New("asset name is required")
	ErrInvalidPrice         = errors.New("price must not be negative")
	ErrInvalidPurchaseDate  = errors.New("purchase date must use YYYY-MM-DD")
	ErrInvalidStatus        = errors.New("invalid asset status")
	ErrInvalidRetiredDate   = errors.New("retired date must use YYYY-MM-DD, be between purchase date and today, and be empty for ACTIVE")
	ErrInvalidAssetMetadata = errors.New("invalid asset metadata")
	ErrInvalidScope         = errors.New("scope must be CURRENT, ARCHIVED, or ALL")
	ErrInvalidArchiveAction = errors.New("action must be ARCHIVE or RESTORE")
	ErrAssetArchived        = errors.New("restore archived asset before editing")
	ErrAssetNotFound        = errors.New("asset not found")
)

type AssetView struct {
	IconKey         string   `json:"icon_key"`
	ArchivedAt      string   `json:"archived_at"`
	RetiredDate     string   `json:"retired_date"`
	ID              string   `json:"id"`
	Name            string   `json:"name"`
	PriceCents      int64    `json:"price_cents"`
	PurchaseDate    string   `json:"purchase_date"`
	Status          string   `json:"status"`
	ImageURL        string   `json:"image_url"`
	HeldDays        int      `json:"held_days"`
	DailyCostCents  int64    `json:"daily_cost_cents"`
	CreatedAt       string   `json:"created_at"`
	UpdatedAt       string   `json:"updated_at"`
	PurchaseChannel string   `json:"purchase_channel"`
	WarrantyEndDate string   `json:"warranty_end_date"`
	Notes           string   `json:"notes"`
	Tags            []string `json:"tags"`
}

type CreateAssetInput struct {
	IconKey         string   `json:"icon_key"`
	Name            string   `json:"name"`
	PriceCents      int64    `json:"price_cents"`
	PurchaseDate    string   `json:"purchase_date"`
	ImageURL        string   `json:"image_url"`
	PurchaseChannel string   `json:"purchase_channel"`
	WarrantyEndDate string   `json:"warranty_end_date"`
	Notes           string   `json:"notes"`
	Tags            []string `json:"tags"`
}

type UpdateAssetInput struct {
	IconKey         string    `json:"icon_key"`
	ID              string    `json:"id"`
	Name            string    `json:"name"`
	PriceCents      int64     `json:"price_cents"`
	PurchaseDate    string    `json:"purchase_date"`
	PurchaseChannel *string   `json:"purchase_channel"`
	WarrantyEndDate *string   `json:"warranty_end_date"`
	Notes           *string   `json:"notes"`
	Tags            *[]string `json:"tags"`
}

type UpdateAssetStatusInput struct {
	ID          string
	Status      string
	RetiredDate string
}

type AssetService struct {
	repository repository.AssetRepository
	ownerID    string
	now        func() time.Time
}

func NewAssetService(repository repository.AssetRepository) *AssetService {
	return &AssetService{repository: repository, now: time.Now}
}

func (s *AssetService) ForOwner(ownerID string) *AssetService {
	return &AssetService{repository: s.repository, ownerID: ownerID, now: s.now}
}

func (s *AssetService) List(status string) ([]AssetView, error) {
	return s.ListScope(status, "")
}

func (s *AssetService) ListScope(status, scope string) ([]AssetView, error) {
	var archived *bool
	switch scope {
	case "", "CURRENT":
		value := false
		archived = &value
	case "ARCHIVED":
		value := true
		archived = &value
	case "ALL":
	default:
		return nil, ErrInvalidScope
	}
	var filter *domain.AssetStatus
	if status != "" {
		parsed := domain.AssetStatus(strings.ToUpper(status))
		if !parsed.Valid() {
			return nil, ErrInvalidStatus
		}
		filter = &parsed
	}

	var assets []domain.Asset
	var err error
	if s.ownerID == "" {
		assets, err = s.repository.List(filter, archived)
	} else if owned, ok := s.repository.(repository.OwnerAssetRepository); ok {
		assets, err = owned.ListForOwner(s.ownerID, filter, archived)
	} else {
		return nil, fmt.Errorf("list assets: owner-scoped repository unavailable")
	}
	if err != nil {
		return nil, fmt.Errorf("list assets: %w", err)
	}
	views := make([]AssetView, 0, len(assets))
	for _, asset := range assets {
		views = append(views, s.toView(asset))
	}
	return views, nil
}

func (s *AssetService) Get(id string) (AssetView, error) {
	asset, err := s.get(id)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return AssetView{}, ErrAssetNotFound
		}
		return AssetView{}, fmt.Errorf("get asset: %w", err)
	}
	return s.toView(*asset), nil
}

func (s *AssetService) Create(input CreateAssetInput) (AssetView, error) {
	name, purchaseDate, err := validateEditable(input.Name, input.PriceCents, input.PurchaseDate)
	if err != nil {
		return AssetView{}, err
	}

	iconKey, err := normalizeIconKey(input.IconKey)
	if err != nil {
		return AssetView{}, err
	}
	asset := domain.Asset{
		OwnerID:      s.ownerID,
		IconKey:      iconKey,
		ID:           newID(),
		Name:         name,
		PriceCents:   input.PriceCents,
		PurchaseDate: purchaseDate,
		Status:       domain.AssetStatusActive,
		ImageURL:     strings.TrimSpace(input.ImageURL),
	}
	if err := applyMetadata(&asset, input.PurchaseChannel, input.WarrantyEndDate, input.Notes, &input.Tags, purchaseDate); err != nil {
		return AssetView{}, err
	}
	if err := s.create(&asset); err != nil {
		return AssetView{}, fmt.Errorf("create asset: %w", err)
	}
	return s.toView(asset), nil
}

// Update replaces the editable fields of an existing asset. Status, retirement
// and image are managed by separate flows and are never touched here.
func (s *AssetService) Update(input UpdateAssetInput) (AssetView, error) {
	name, purchaseDate, err := validateEditable(input.Name, input.PriceCents, input.PurchaseDate)
	if err != nil {
		return AssetView{}, err
	}

	current, err := s.get(input.ID)
	if errors.Is(err, repository.ErrNotFound) {
		return AssetView{}, ErrAssetNotFound
	}
	if err != nil {
		return AssetView{}, fmt.Errorf("get asset for update: %w", err)
	}
	if current.ArchivedAt != nil {
		return AssetView{}, ErrAssetArchived
	}
	if current.RetiredAt != nil && purchaseDate.After(*current.RetiredAt) {
		return AssetView{}, ErrInvalidRetiredDate
	}

	iconKey := input.IconKey
	if iconKey == "" {
		iconKey = current.IconKey
	}
	iconKey, err = normalizeIconKey(iconKey)
	if err != nil {
		return AssetView{}, err
	}
	asset := domain.Asset{
		IconKey:      iconKey,
		ID:           input.ID,
		Name:         name,
		PriceCents:   input.PriceCents,
		PurchaseDate: purchaseDate,
	}
	asset.PurchaseChannel = current.PurchaseChannel
	asset.WarrantyEndDate = current.WarrantyEndDate
	asset.Notes = current.Notes
	asset.Tags = current.Tags
	if err := applyMetadata(&asset, optionalValue(input.PurchaseChannel, current.PurchaseChannel), optionalDate(input.WarrantyEndDate, current.WarrantyEndDate), optionalValue(input.Notes, current.Notes), optionalTags(input.Tags, current.Tags), purchaseDate); err != nil {
		return AssetView{}, err
	}
	if err := s.update(&asset); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return AssetView{}, ErrAssetNotFound
		}
		return AssetView{}, fmt.Errorf("update asset: %w", err)
	}
	return s.toView(asset), nil
}

// UpdateStatus sets the requested lifecycle state; repeating the same request is
// safe. Reactivation resumes held-day calculation from the original purchase day.
func (s *AssetService) UpdateStatus(input UpdateAssetStatusInput) (AssetView, error) {
	status := domain.AssetStatus(input.Status)
	if !status.Valid() {
		return AssetView{}, ErrInvalidStatus
	}
	var retiredAt *time.Time
	if status == domain.AssetStatusRetired {
		date, err := time.Parse(time.DateOnly, input.RetiredDate)
		today, _ := time.Parse(time.DateOnly, s.now().Format(time.DateOnly))
		if err != nil || date.After(today) {
			return AssetView{}, ErrInvalidRetiredDate
		}
		retiredAt = &date
	} else if input.RetiredDate != "" {
		return AssetView{}, ErrInvalidRetiredDate
	}
	asset, err := s.get(input.ID)
	if errors.Is(err, repository.ErrNotFound) {
		return AssetView{}, ErrAssetNotFound
	}
	if err != nil {
		return AssetView{}, fmt.Errorf("get asset for status update: %w", err)
	}
	if asset.ArchivedAt != nil {
		return AssetView{}, ErrAssetArchived
	}
	if retiredAt != nil && retiredAt.Before(asset.PurchaseDate) {
		return AssetView{}, ErrInvalidRetiredDate
	}
	asset.Status = status
	asset.RetiredAt = retiredAt
	if err := s.updateStatus(asset); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return AssetView{}, ErrAssetNotFound
		}
		return AssetView{}, fmt.Errorf("update asset status: %w", err)
	}
	return s.toView(*asset), nil
}

// UpdateArchive preserves lifecycle and financial fields. Archived assets remain
// recoverable indefinitely and can be read by ID.
func (s *AssetService) UpdateArchive(id, action string) (AssetView, error) {
	if action != "ARCHIVE" && action != "RESTORE" {
		return AssetView{}, ErrInvalidArchiveAction
	}
	asset, err := s.get(id)
	if errors.Is(err, repository.ErrNotFound) {
		return AssetView{}, ErrAssetNotFound
	}
	if err != nil {
		return AssetView{}, fmt.Errorf("get asset for archive update: %w", err)
	}
	if action == "ARCHIVE" {
		if asset.ArchivedAt == nil {
			now := s.now()
			asset.ArchivedAt = &now
		}
	} else {
		asset.ArchivedAt = nil
	}
	if err := s.updateArchive(asset); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return AssetView{}, ErrAssetNotFound
		}
		return AssetView{}, fmt.Errorf("update asset archive: %w", err)
	}
	return s.toView(*asset), nil
}

func (s *AssetService) get(id string) (*domain.Asset, error) {
	if s.ownerID == "" {
		return s.repository.Get(id)
	}
	owned, ok := s.repository.(repository.OwnerAssetRepository)
	if !ok {
		return nil, fmt.Errorf("owner-scoped repository unavailable")
	}
	return owned.GetForOwner(s.ownerID, id)
}

func (s *AssetService) create(asset *domain.Asset) error {
	if s.ownerID == "" {
		return s.repository.Create(asset)
	}
	owned, ok := s.repository.(repository.OwnerAssetRepository)
	if !ok {
		return fmt.Errorf("owner-scoped repository unavailable")
	}
	return owned.CreateForOwner(s.ownerID, asset)
}

func (s *AssetService) update(asset *domain.Asset) error {
	if s.ownerID == "" {
		return s.repository.Update(asset)
	}
	owned, ok := s.repository.(repository.OwnerAssetRepository)
	if !ok {
		return fmt.Errorf("owner-scoped repository unavailable")
	}
	return owned.UpdateForOwner(s.ownerID, asset)
}

func (s *AssetService) updateStatus(asset *domain.Asset) error {
	if s.ownerID == "" {
		return s.repository.UpdateStatus(asset)
	}
	owned, ok := s.repository.(repository.OwnerAssetRepository)
	if !ok {
		return fmt.Errorf("owner-scoped repository unavailable")
	}
	return owned.UpdateStatusForOwner(s.ownerID, asset)
}

func (s *AssetService) updateArchive(asset *domain.Asset) error {
	if s.ownerID == "" {
		return s.repository.UpdateArchive(asset)
	}
	owned, ok := s.repository.(repository.OwnerAssetRepository)
	if !ok {
		return fmt.Errorf("owner-scoped repository unavailable")
	}
	return owned.UpdateArchiveForOwner(s.ownerID, asset)
}

// validateEditable enforces the shared rules for the user-editable fields.
func validateEditable(rawName string, priceCents int64, rawPurchaseDate string) (string, time.Time, error) {
	name := strings.TrimSpace(rawName)
	if name == "" {
		return "", time.Time{}, ErrInvalidName
	}
	if priceCents < 0 {
		return "", time.Time{}, ErrInvalidPrice
	}
	purchaseDate, err := time.Parse(time.DateOnly, rawPurchaseDate)
	if err != nil {
		return "", time.Time{}, ErrInvalidPurchaseDate
	}
	return name, purchaseDate, nil
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
	retiredDate := ""
	archivedAt := ""
	if asset.ArchivedAt != nil {
		archivedAt = asset.ArchivedAt.Format(time.RFC3339)
	}
	if asset.Status == domain.AssetStatusRetired && asset.RetiredAt != nil {
		retiredDate = asset.RetiredAt.Format(time.DateOnly)
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

	iconKey := asset.IconKey
	if iconKey == "" {
		iconKey = "devices"
	}
	return AssetView{
		IconKey:         iconKey,
		RetiredDate:     retiredDate,
		ArchivedAt:      archivedAt,
		ID:              asset.ID,
		Name:            asset.Name,
		PriceCents:      asset.PriceCents,
		PurchaseDate:    asset.PurchaseDate.Format(time.DateOnly),
		Status:          string(asset.Status),
		ImageURL:        asset.ImageURL,
		HeldDays:        days,
		DailyCostCents:  dailyCost,
		CreatedAt:       asset.CreatedAt.Format(time.RFC3339),
		UpdatedAt:       asset.UpdatedAt.Format(time.RFC3339),
		PurchaseChannel: asset.PurchaseChannel,
		WarrantyEndDate: formatOptionalDate(asset.WarrantyEndDate),
		Notes:           asset.Notes,
		Tags:            append([]string{}, asset.Tags...),
	}
}

func applyMetadata(asset *domain.Asset, purchaseChannel, warrantyEndDate, notes string, tags *[]string, purchaseDate time.Time) error {
	channel := strings.TrimSpace(purchaseChannel)
	if len([]rune(channel)) > 100 || len([]rune(notes)) > 2000 {
		return ErrInvalidAssetMetadata
	}
	var warranty *time.Time
	if strings.TrimSpace(warrantyEndDate) != "" {
		parsed, err := time.Parse(time.DateOnly, strings.TrimSpace(warrantyEndDate))
		if err != nil {
			return ErrInvalidAssetMetadata
		}
		warranty = &parsed
	}
	if tags == nil {
		tags = &[]string{}
	}
	normalized := make([]string, 0, len(*tags))
	seen := make(map[string]struct{}, len(*tags))
	for _, raw := range *tags {
		tag := strings.TrimSpace(raw)
		if tag == "" {
			continue
		}
		if len([]rune(tag)) > 30 {
			return ErrInvalidAssetMetadata
		}
		if _, exists := seen[tag]; exists {
			continue
		}
		seen[tag] = struct{}{}
		normalized = append(normalized, tag)
	}
	if len(normalized) > 20 {
		return ErrInvalidAssetMetadata
	}
	asset.PurchaseChannel, asset.WarrantyEndDate, asset.Notes, asset.Tags = channel, warranty, strings.TrimSpace(notes), normalized
	return nil
}

func optionalValue(value *string, fallback string) string {
	if value == nil {
		return fallback
	}
	return *value
}

func optionalDate(value *string, fallback *time.Time) string {
	if value == nil {
		if fallback == nil {
			return ""
		}
		return fallback.Format(time.DateOnly)
	}
	return *value
}

func optionalTags(value *[]string, fallback []string) *[]string {
	if value == nil {
		return &fallback
	}
	return value
}

func formatOptionalDate(value *time.Time) string {
	if value == nil {
		return ""
	}
	return value.Format(time.DateOnly)
}

func newID() string {
	var value [16]byte
	if _, err := rand.Read(value[:]); err != nil {
		panic(fmt.Sprintf("generate asset id: %v", err))
	}
	return hex.EncodeToString(value[:])
}

func normalizeIconKey(key string) (string, error) {
	if key == "" {
		return "devices", nil
	}
	switch key {
	case "devices", "laptop", "phone", "tablet", "headphones", "watch", "camera", "gamepad", "book", "keyboard", "bicycle", "home":
		return key, nil
	default:
		return "", ErrInvalidIcon
	}
}
