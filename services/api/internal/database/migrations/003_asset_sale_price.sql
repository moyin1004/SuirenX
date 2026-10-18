ALTER TABLE asset_records ADD COLUMN sale_price_cents INTEGER CHECK (sale_price_cents IS NULL OR sale_price_cents >= 0);
