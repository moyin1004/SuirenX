package service

import (
	"encoding/json"
	"testing"
	"time"
)

func TestSalePricePresenceAndValidation(t *testing.T) {
	for _, tc := range []struct {
		raw     string
		present bool
		valid   bool
	}{
		{`{}`, false, true}, {`{"sale_price_cents":null}`, true, true},
		{`{"sale_price_cents":0}`, true, true}, {`{"sale_price_cents":80050}`, true, true},
		{`{"sale_price_cents":-1}`, true, false},
	} {
		var input SyncAssetInput
		if err := json.Unmarshal([]byte(tc.raw), &input); err != nil {
			t.Fatal(err)
		}
		if input.SalePriceProvided != tc.present {
			t.Fatalf("presence %s", tc.raw)
		}
		input.ID = "a"
		input.Name = "A"
		input.PurchaseDate = "2020-01-01"
		input.Status = "ACTIVE"
		if err := validateSyncAsset(input, time.Now()); (err == nil) != tc.valid {
			t.Fatalf("validation %s: %v", tc.raw, err)
		}
	}
}
