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

func (r *memoryAssetRepository) List(status *domain.AssetStatus, archived *bool) ([]domain.Asset, error) {
	var assets []domain.Asset
	for _, asset := range r.assets {
		if (status == nil || asset.Status == *status) && (archived == nil || (asset.ArchivedAt != nil) == *archived) {
			assets = append(assets, asset)
		}
	}
	return assets, nil
}

func (r *memoryAssetRepository) Get(id string) (*domain.Asset, error) {
	for i := range r.assets {
		if r.assets[i].ID == id {
			asset := r.assets[i]
			return &asset, nil
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
			r.assets[i].IconKey = asset.IconKey
			r.assets[i].PurchaseChannel = asset.PurchaseChannel
			r.assets[i].WarrantyEndDate = asset.WarrantyEndDate
			r.assets[i].Notes = asset.Notes
			r.assets[i].Tags = append([]string{}, asset.Tags...)
			r.assets[i].UpdatedAt = time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC)
			*asset = r.assets[i]
			return nil
		}
	}
	return repository.ErrNotFound
}

func TestMetadataIsNormalizedAndOptionalUpdateFieldsPreserveOrClear(t *testing.T) {
	repo := &memoryAssetRepository{}
	s := NewAssetService(repo)
	s.now = func() time.Time { return time.Date(2026, 9, 6, 12, 0, 0, 0, time.UTC) }
	created, err := s.Create(CreateAssetInput{
		Name: "Camera", PriceCents: 10000, PurchaseDate: "2026-09-01",
		PurchaseChannel: "  门店  ", WarrantyEndDate: "2027-09-01", Notes: "  test note  ",
		Tags: []string{"  旅行", "旅行", "摄影"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if created.PurchaseChannel != "门店" || created.WarrantyEndDate != "2027-09-01" || created.Notes != "test note" {
		t.Fatalf("metadata was not normalized: %+v", created)
	}
	if len(created.Tags) != 2 || created.Tags[0] != "旅行" || created.Tags[1] != "摄影" {
		t.Fatalf("tags were not normalized: %#v", created.Tags)
	}

	preserved, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "Camera 2", PriceCents: 10000, PurchaseDate: "2026-09-01"})
	if err != nil {
		t.Fatal(err)
	}
	if preserved.PurchaseChannel != "门店" || preserved.WarrantyEndDate != "2027-09-01" || len(preserved.Tags) != 2 {
		t.Fatalf("nil metadata fields should preserve values: %+v", preserved)
	}
	empty, emptyTags := "", []string{}
	cleared, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "Camera 2", PriceCents: 10000, PurchaseDate: "2026-09-01", PurchaseChannel: &empty, WarrantyEndDate: &empty, Notes: &empty, Tags: &emptyTags})
	if err != nil {
		t.Fatal(err)
	}
	if cleared.PurchaseChannel != "" || cleared.WarrantyEndDate != "" || cleared.Notes != "" || len(cleared.Tags) != 0 {
		t.Fatalf("explicit empty metadata should clear values: %+v", cleared)
	}
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

func (r *memoryAssetRepository) UpdateStatus(asset *domain.Asset) error {
	for i := range r.assets {
		if r.assets[i].ID == asset.ID {
			r.assets[i].Status = asset.Status
			r.assets[i].RetiredAt = asset.RetiredAt
			*asset = r.assets[i]
			return nil
		}
	}
	return repository.ErrNotFound
}

func TestStatusLifecycle(t *testing.T) {
	s := NewAssetService(&memoryAssetRepository{})
	s.now = func() time.Time { return time.Date(2026, 9, 5, 23, 0, 0, 0, time.FixedZone("CST", 8*3600)) }
	created, err := s.Create(CreateAssetInput{Name: "Keyboard", PriceCents: 10000, PurchaseDate: "2026-09-01"})
	if err != nil {
		t.Fatal(err)
	}
	for _, date := range []string{"2026-09-01", "2026-09-03", "2026-09-05"} {
		retired, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: "RETIRED", RetiredDate: date})
		if err != nil {
			t.Fatal(err)
		}
		if retired.Status != "RETIRED" || retired.RetiredDate != date {
			t.Fatalf("retired: %+v", retired)
		}
		// Repeating PUT and advancing the clock must not change the retirement metrics.
		s.now = func() time.Time { return time.Date(2026, 9, 10, 0, 0, 0, 0, time.UTC) }
		repeated, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: "RETIRED", RetiredDate: date})
		if err != nil || repeated.HeldDays != retired.HeldDays || repeated.DailyCostCents != retired.DailyCostCents {
			t.Fatalf("repeat: %+v %v", repeated, err)
		}
		if date == "2026-09-03" && (retired.HeldDays != 3 || retired.DailyCostCents != 3333) {
			t.Fatalf("inclusive metrics: %+v", retired)
		}
	}
	active, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: "ACTIVE"})
	if err != nil || active.Status != "ACTIVE" || active.RetiredDate != "" || active.HeldDays != 10 || active.DailyCostCents != 1000 {
		t.Fatalf("reactivated: %+v %v", active, err)
	}
	stored, _ := s.repository.Get(created.ID)
	if stored.RetiredAt != nil {
		t.Fatal("retirement not cleared")
	}
}

func TestStatusValidationAndEditRetirementInvariant(t *testing.T) {
	s := NewAssetService(&memoryAssetRepository{})
	s.now = func() time.Time { return time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC) }
	created, err := s.Create(CreateAssetInput{Name: "Keyboard", PriceCents: 10000, PurchaseDate: "2026-09-01"})
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		status, date string
		want         error
	}{
		{"", "", ErrInvalidStatus}, {"retired", "2026-09-03", ErrInvalidStatus}, {"UNKNOWN", "", ErrInvalidStatus},
		{"RETIRED", "", ErrInvalidRetiredDate}, {"RETIRED", "2026-02-30", ErrInvalidRetiredDate},
		{"RETIRED", "2026-09-06", ErrInvalidRetiredDate}, {"RETIRED", "2026-08-31", ErrInvalidRetiredDate},
		{"RETIRED", "2026-09-03T00:00:00Z", ErrInvalidRetiredDate}, {"ACTIVE", "2026-09-03", ErrInvalidRetiredDate},
	} {
		_, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: tc.status, RetiredDate: tc.date})
		if !errors.Is(err, tc.want) {
			t.Errorf("%+v: %v", tc, err)
		}
	}
	stored, _ := s.Get(created.ID)
	if stored.Status != "ACTIVE" || stored.RetiredDate != "" {
		t.Fatalf("invalid request mutated: %+v", stored)
	}
	if _, err := s.UpdateStatus(UpdateAssetStatusInput{ID: "missing", Status: "ACTIVE"}); !errors.Is(err, ErrAssetNotFound) {
		t.Fatal(err)
	}
	if _, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: "RETIRED", RetiredDate: "2026-09-03"}); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "New", PriceCents: 0, PurchaseDate: "2026-09-04"}); !errors.Is(err, ErrInvalidRetiredDate) {
		t.Fatal(err)
	}
	updated, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "New", PriceCents: 0, PurchaseDate: "2026-09-03"})
	if err != nil || updated.RetiredDate != "2026-09-03" || updated.Status != "RETIRED" || updated.HeldDays != 1 {
		t.Fatalf("edit retired: %+v %v", updated, err)
	}
}

func (r *memoryAssetRepository) UpdateArchive(asset *domain.Asset) error {
	for i := range r.assets {
		if r.assets[i].ID == asset.ID {
			if asset.ArchivedAt == nil || r.assets[i].ArchivedAt == nil {
				r.assets[i].ArchivedAt = asset.ArchivedAt
			}
			*asset = r.assets[i]
			return nil
		}
	}
	return repository.ErrNotFound
}

func TestArchiveAndRestorePreserveLifecycle(t *testing.T) {
	for _, status := range []string{"ACTIVE", "RETIRED"} {
		t.Run(status, func(t *testing.T) {
			s := NewAssetService(&memoryAssetRepository{})
			now := time.Date(2026, 9, 5, 12, 0, 0, 0, time.UTC)
			s.now = func() time.Time { return now }
			created, err := s.Create(CreateAssetInput{Name: "Keyboard", PriceCents: 10000, PurchaseDate: "2026-09-01", ImageURL: "image"})
			if err != nil {
				t.Fatal(err)
			}
			if status == "RETIRED" {
				created, err = s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: status, RetiredDate: "2026-09-03"})
				if err != nil {
					t.Fatal(err)
				}
			}
			archived, err := s.UpdateArchive(created.ID, "ARCHIVE")
			if err != nil || archived.ArchivedAt != now.Format(time.RFC3339) {
				t.Fatalf("archive: %+v %v", archived, err)
			}
			now = now.Add(time.Hour)
			again, err := s.UpdateArchive(created.ID, "ARCHIVE")
			if err != nil || again.ArchivedAt != archived.ArchivedAt {
				t.Fatalf("retry: %+v %v", again, err)
			}
			current, _ := s.List("")
			if len(current) != 0 {
				t.Fatalf("archive in default list: %+v", current)
			}
			for _, scope := range []string{"ARCHIVED", "ALL"} {
				list, err := s.ListScope(status, scope)
				if err != nil || len(list) != 1 {
					t.Fatalf("scope %s: %+v %v", scope, list, err)
				}
			}
			if _, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "New", PriceCents: 0, PurchaseDate: "2026-09-01"}); !errors.Is(err, ErrAssetArchived) {
				t.Fatal(err)
			}
			if _, err := s.UpdateStatus(UpdateAssetStatusInput{ID: created.ID, Status: "ACTIVE"}); !errors.Is(err, ErrAssetArchived) {
				t.Fatal(err)
			}
			if err := s.SeedExamples(); err != nil {
				t.Fatal(err)
			}
			all, _ := s.ListScope("", "ALL")
			if len(all) != 1 {
				t.Fatal("archiving all assets caused example reseeding")
			}
			for i := 0; i < 2; i++ {
				restored, err := s.UpdateArchive(created.ID, "RESTORE")
				if err != nil || restored.ArchivedAt != "" || restored.Status != created.Status || restored.RetiredDate != created.RetiredDate || restored.PriceCents != created.PriceCents || restored.ImageURL != created.ImageURL {
					t.Fatalf("restore: %+v %v", restored, err)
				}
			}
			current, _ = s.List("")
			if len(current) != 1 {
				t.Fatal("restored asset missing")
			}
		})
	}
}

func TestArchiveValidation(t *testing.T) {
	s := NewAssetService(&memoryAssetRepository{})
	for _, action := range []string{"", "archive", "DELETE"} {
		if _, err := s.UpdateArchive("missing", action); !errors.Is(err, ErrInvalidArchiveAction) {
			t.Fatal(err)
		}
	}
	if _, err := s.UpdateArchive("missing", "ARCHIVE"); !errors.Is(err, ErrAssetNotFound) {
		t.Fatal(err)
	}
	if _, err := s.ListScope("", "garbage"); !errors.Is(err, ErrInvalidScope) {
		t.Fatal(err)
	}
}

func TestIconSelectionValidationAndPreservation(t *testing.T) {
	s := NewAssetService(&memoryAssetRepository{})
	for _, key := range []string{"", "devices", "laptop", "phone", "tablet", "headphones", "watch", "camera", "gamepad", "book", "keyboard", "bicycle", "home"} {
		asset, err := s.Create(CreateAssetInput{Name: "A", PriceCents: 100, PurchaseDate: "2020-01-01", IconKey: key})
		if err != nil {
			t.Fatal(err)
		}
		expected := key
		if key == "" {
			expected = "devices"
		}
		if asset.IconKey != expected {
			t.Fatalf("icon=%q want %q", asset.IconKey, expected)
		}
	}
	if _, err := s.Create(CreateAssetInput{Name: "A", PriceCents: 100, PurchaseDate: "2020-01-01", IconKey: "unknown"}); !errors.Is(err, ErrInvalidIcon) {
		t.Fatal(err)
	}
	created, err := s.Create(CreateAssetInput{Name: "A", PriceCents: 100, PurchaseDate: "2020-01-01", IconKey: "laptop"})
	if err != nil {
		t.Fatal(err)
	}
	// Older clients omit the new field: keep the user's selected icon.
	updated, err := s.Update(UpdateAssetInput{ID: created.ID, Name: "B", PriceCents: 100, PurchaseDate: "2020-01-01"})
	if err != nil || updated.IconKey != "laptop" {
		t.Fatalf("legacy edit: %+v %v", updated, err)
	}
	_, err = s.Update(UpdateAssetInput{ID: created.ID, Name: "B", PriceCents: 100, PurchaseDate: "2020-01-01", IconKey: "bad"})
	if !errors.Is(err, ErrInvalidIcon) {
		t.Fatal(err)
	}
	got, _ := s.Get(created.ID)
	if got.IconKey != "laptop" {
		t.Fatal("invalid key mutated icon")
	}
	updated, err = s.Update(UpdateAssetInput{ID: created.ID, Name: "B", PriceCents: 100, PurchaseDate: "2020-01-01", IconKey: "devices"})
	if err != nil || updated.IconKey != "devices" {
		t.Fatalf("reset: %+v %v", updated, err)
	}
}
