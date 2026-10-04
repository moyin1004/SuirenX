-- v0.1.1 server-side administration, shared web configuration and scoped API tokens.
ALTER TABLE accounts ADD COLUMN role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'SUPERADMIN'));
ALTER TABLE accounts ADD COLUMN disabled_at DATETIME;
CREATE INDEX idx_accounts_role ON accounts(role);
CREATE INDEX idx_accounts_disabled_at ON accounts(disabled_at);

CREATE TABLE config_files (
    config_key TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    content TEXT NOT NULL,
    created_by TEXT NOT NULL,
    updated_by TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME
);
CREATE UNIQUE INDEX idx_config_files_display_name_active ON config_files(display_name) WHERE deleted_at IS NULL;
CREATE INDEX idx_config_files_deleted_at ON config_files(deleted_at);

CREATE TABLE api_tokens (
    id TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    transport_mode TEXT NOT NULL CHECK (transport_mode IN ('BEARER', 'QUERY')),
    created_by TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME,
    revoked_at DATETIME,
    last_used_at DATETIME
);
CREATE INDEX idx_api_tokens_revoked_at ON api_tokens(revoked_at);
CREATE INDEX idx_api_tokens_expires_at ON api_tokens(expires_at);
CREATE TABLE api_token_config_scopes (
    token_id TEXT NOT NULL,
    config_key TEXT NOT NULL,
    PRIMARY KEY (token_id, config_key),
    FOREIGN KEY (token_id) REFERENCES api_tokens(id) ON DELETE CASCADE
);
CREATE INDEX idx_api_token_config_scopes_config_key ON api_token_config_scopes(config_key);

CREATE TABLE server_settings (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_by TEXT NOT NULL,
    updated_at DATETIME NOT NULL
);
INSERT INTO server_settings(setting_key, setting_value, updated_by, updated_at)
VALUES ('config_max_bytes', '10000000', 'system', CURRENT_TIMESTAMP);

CREATE TABLE admin_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_id TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    action TEXT NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at DESC, id DESC);
