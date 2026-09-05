-- Baseline schema. The early-development snapshots (assets table, archive
-- columns, icon key) are squashed here; all later changes must arrive as new
-- consecutively numbered migration files.
CREATE TABLE asset_records (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    price_cents INTEGER NOT NULL,
    purchase_date DATETIME,
    retired_at DATETIME,
    status TEXT NOT NULL,
    image_url TEXT,
    archived_at DATETIME,
    icon_key TEXT,
    created_at DATETIME,
    updated_at DATETIME
);
CREATE INDEX idx_asset_records_status ON asset_records(status);
CREATE INDEX idx_asset_records_archived_at ON asset_records(archived_at);
