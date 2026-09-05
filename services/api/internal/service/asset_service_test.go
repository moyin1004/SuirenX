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
