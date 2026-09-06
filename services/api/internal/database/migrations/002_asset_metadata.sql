ALTER TABLE asset_records ADD COLUMN purchase_channel TEXT NOT NULL DEFAULT '';
ALTER TABLE asset_records ADD COLUMN warranty_end_date DATETIME;
ALTER TABLE asset_records ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE asset_records ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
