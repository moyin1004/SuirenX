package service

import (
	"strings"
	"testing"
	"time"
)

func TestSyncAcceptsOptionalLocationAndUnicodeText(t *testing.T) {
	if err := validateSyncExpiry(SyncExpiryInput{ID: "expiry", Name: "Milk", Category: "Food", PackageExpiryDate: "2030-01-01", Status: "IN_USE", Location: ""}); err != nil {
		t.Fatal(err)
	}
	if err := validateSyncAsset(SyncAssetInput{ID: "asset", Name: "Phone", PurchaseDate: "2026-01-01", Status: "ACTIVE", Notes: strings.Repeat("中", 2000), Tags: []string{strings.Repeat("中", 30)}}, time.Now()); err != nil {
		t.Fatal(err)
	}
}
