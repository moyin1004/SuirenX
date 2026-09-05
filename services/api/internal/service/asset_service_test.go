package service

import (
	"errors"
	"testing"
	"time"

	"github.com/moyin1004/suirenx/services/api/internal/domain"
	"github.com/moyin1004/suirenx/services/api/internal/repository"
)

type memoryAssetRepository struct {
	assets []domain.Asset
}

func (r *memoryAssetRepository) List(status *domain.AssetStatus) ([]domain.Asset, error) {
	if status == nil {
		return r.assets, nil
	}
	var assets []domain.Asset
	for _, asset := range r.assets {
		if asset.Status == *status {
			assets = append(assets, asset)
		}
	}
	return assets, nil
}

func (r *memoryAssetRepository) Get(id string) (*domain.Asset, error) {
	for i := range r.assets {
		if r.assets[i].ID == id {
			return &r.assets[i], nil
		}
	}
	return nil, repository.ErrNotFound
}

func (r *memoryAssetRepository) Create(asset *domain.Asset) error {
	asset.CreatedAt = time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC)
	asset.UpdatedAt = asset.CreatedAt
	r.assets = append(r.assets, *asset)
	return nil
}

func (r *memoryAssetRepository) Update(asset *domain.Asset) error {
	for i := range r.assets {
		if r.assets[i].ID == asset.ID {
			r.assets[i].Name = asset.Name
			r.assets[i].PriceCents = asset.PriceCents
			r.assets[i].PurchaseDate = asset.PurchaseDate
			r.assets[i].UpdatedAt = time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC)
			*asset = r.assets[i]
			return nil
		}
	}
	return repository.ErrNotFound
}

func (r *memoryAssetRepository) Count() (int64, error) {
	return int64(len(r.assets)), nil
}

func TestCreateCalculatesDailyCost(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }

	asset, err := s.Create(CreateAssetInput{
		Name:         "Keyboard",
		PriceCents:   10000,
		PurchaseDate: "2026-09-01",
	})
	if err != nil {
		t.Fatal(err)
	}
	if asset.HeldDays != 5 {
		t.Fatalf("held days = %d, want 5", asset.HeldDays)
	}
	if asset.DailyCostCents != 2000 {
		t.Fatalf("daily cost = %d, want 2000", asset.DailyCostCents)
	}
}

func TestGetReturnsAssetAndNotFound(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }

	created, err := s.Create(CreateAssetInput{
		Name:         "Keyboard",
		PriceCents:   10000,
		PurchaseDate: "2026-09-01",
	})
	if err != nil {
		t.Fatal(err)
	}

	got, err := s.Get(created.ID)
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != created.ID || got.Name != "Keyboard" {
		t.Fatalf("unexpected asset: %+v", got)
	}
	if got.HeldDays != 5 || got.DailyCostCents != 2000 {
		t.Fatalf("derived values: held=%d daily=%d", got.HeldDays, got.DailyCostCents)
	}

	if _, err := s.Get("missing-id"); !errors.Is(err, ErrAssetNotFound) {
		t.Fatalf("missing id: got %v, want ErrAssetNotFound", err)
	}
}

func TestUpdateChangesEditableFieldsAndRecomputesDerivedValues(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }

	created, err := s.Create(CreateAssetInput{
		Name:         "Keyboard",
		PriceCents:   10000,
		PurchaseDate: "2026-09-01",
	})
	if err != nil {
		t.Fatal(err)
	}

	updated, err := s.Update(UpdateAssetInput{
		ID:           created.ID,
		Name:         "  Mechanical Keyboard  ",
		PriceCents:   20000,
		PurchaseDate: "2026-09-03",
	})
	if err != nil {
		t.Fatal(err)
	}
	if updated.Name != "Mechanical Keyboard" {
		t.Fatalf("name = %q, want trimmed new name", updated.Name)
	}
	if updated.PriceCents != 20000 {
		t.Fatalf("price = %d, want 20000", updated.PriceCents)
	}
	if updated.PurchaseDate != "2026-09-03" {
		t.Fatalf("purchase date = %q, want 2026-09-03", updated.PurchaseDate)
	}
	// Held days cover 2026-09-03 through 2026-09-05 inclusive.
	if updated.HeldDays != 3 {
		t.Fatalf("held days = %d, want 3", updated.HeldDays)
	}
	if updated.DailyCostCents != 6667 {
		t.Fatalf("daily cost = %d, want 6667", updated.DailyCostCents)
	}
	if updated.Status != string(domain.AssetStatusActive) {
		t.Fatalf("status = %q, want ACTIVE preserved", updated.Status)
	}

	// The stored record reflects the update as well.
	stored, err := s.Get(created.ID)
	if err != nil {
		t.Fatal(err)
	}
	if stored.Name != "Mechanical Keyboard" || stored.PriceCents != 20000 {
		t.Fatalf("stored asset not updated: %+v", stored)
	}
}

func TestUpdateValidatesInput(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }

	created, err := s.Create(CreateAssetInput{
		Name: "Keyboard", PriceCents: 10000, PurchaseDate: "2026-09-01",
	})
	if err != nil {
		t.Fatal(err)
	}

	if _, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "   ", PriceCents: 10000, PurchaseDate: "2026-09-01"}); !errors.Is(err, ErrInvalidName) {
		t.Fatalf("blank name: got %v, want ErrInvalidName", err)
	}
	if _, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "Keyboard", PriceCents: -1, PurchaseDate: "2026-09-01"}); !errors.Is(err, ErrInvalidPrice) {
		t.Fatalf("negative price: got %v, want ErrInvalidPrice", err)
	}
	if _, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "Keyboard", PriceCents: 10000, PurchaseDate: "09/01/2026"}); !errors.Is(err, ErrInvalidPurchaseDate) {
		t.Fatalf("bad date: got %v, want ErrInvalidPurchaseDate", err)
	}
	if _, err := s.Update(UpdateAssetInput{ID: "missing-id", Name: "Keyboard", PriceCents: 10000, PurchaseDate: "2026-09-01"}); !errors.Is(err, ErrAssetNotFound) {
		t.Fatalf("missing id: got %v, want ErrAssetNotFound", err)
	}

	// Failed validation must not mutate the stored asset.
	stored, err := s.Get(created.ID)
	if err != nil {
		t.Fatal(err)
	}
	if stored.Name != "Keyboard" || stored.PriceCents != 10000 {
		t.Fatalf("asset mutated by failed update: %+v", stored)
	}
}

func TestSeedExamplesDoesNotDuplicateData(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }

	if err := s.SeedExamples(); err != nil {
		t.Fatal(err)
	}
	if err := s.SeedExamples(); err != nil {
		t.Fatal(err)
	}
	if len(repo.assets) != 2 {
		t.Fatalf("seeded assets = %d, want 2", len(repo.assets))
	}
}

func TestDailyCostRoundingDoesNotOverflow(t *testing.T) {
	s := NewAssetService(&memoryAssetRepository{})
	s.now = func() time.Time { return time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC) }
	for _, tc := range []struct {
		price int64
		date  string
		want  int64
	}{
		{9223372036854775807, "2026-09-04", 4611686018427387904},
		{1, "2026-09-04", 1},
		{1, "2026-09-03", 0},
		{2, "2026-09-03", 1},
		{0, "2026-09-05", 0},
	} {
		asset, err := s.Create(CreateAssetInput{Name: "A", PriceCents: tc.price, PurchaseDate: tc.date})
		if err != nil {
			t.Fatal(err)
		}
		if asset.DailyCostCents != tc.want {
			t.Errorf("price %d on %s: got %d, want %d", tc.price, tc.date, asset.DailyCostCents, tc.want)
		}
	}
}
