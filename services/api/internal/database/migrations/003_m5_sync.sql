-- M5 account ownership, versioned tombstones, and durable sync events.
CREATE TABLE accounts (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash BLOB NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE TABLE auth_tokens (
    token_hash TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES accounts(id) ON DELETE CASCADE
);
CREATE INDEX idx_auth_tokens_owner_id ON auth_tokens(owner_id);
ALTER TABLE asset_records ADD COLUMN owner_id TEXT;
ALTER TABLE asset_records ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE asset_records ADD COLUMN deleted_at DATETIME;
CREATE INDEX idx_asset_records_owner_id ON asset_records(owner_id);
CREATE TABLE sync_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id TEXT NOT NULL,
    asset_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    deleted_at DATETIME,
    snapshot_json TEXT NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_sync_events_owner_cursor ON sync_events(owner_id, id);
CREATE TABLE sync_idempotency (
    owner_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (owner_id, idempotency_key)
);
CREATE TABLE expiry_records (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    package_expiry_date TEXT NOT NULL,
    opened_date TEXT,
    opened_validity_days INTEGER,
    location TEXT NOT NULL,
    notes TEXT NOT NULL,
    status TEXT NOT NULL,
    archived_at DATETIME,
    version INTEGER NOT NULL DEFAULT 1,
    deleted_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE INDEX idx_expiry_records_owner_id ON expiry_records(owner_id);
CREATE TABLE sync_expiry_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id TEXT NOT NULL,
    expiry_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    deleted_at DATETIME,
    snapshot_json TEXT NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_sync_expiry_events_owner_cursor ON sync_expiry_events(owner_id, id);
