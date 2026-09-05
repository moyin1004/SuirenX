# Architecture

SuirenX starts as a monorepo and a modular monolith. Repository boundaries are
kept language-neutral so performance-sensitive Go modules can later be
replaced by C++ without changing mobile clients.

## Data flow

```text
Android (Compose)
      |
      | HTTP + JSON, described by Protobuf IDL
      v
Hertz transport -> asset service -> repository interface -> GORM -> SQLite
```

Protobuf is the API contract and code-generation source. It does not require
binary Protobuf on the wire. SQLite is the server database in the first
version. Android-local Room storage will be introduced when offline behavior
and conflict rules are defined.

`services/api/scripts/generate.sh` pins hz v0.9.7 and generates the transport
models and routes from the IDL. Generated handler entry points adapt requests
to the service and map service views back to API models. A server-scoped
middleware supplies the service without process-global mutable dependencies.
The public JSON uses snake_case, numeric integer cents, string statuses
(`ACTIVE` / `RETIRED`), explicit zero values, and empty arrays rather than null.
Use Hertz JSON rendering, not canonical protojson encoding.

## Migration boundaries

- API messages live under `api/proto` and do not expose GORM models.
- Business logic depends on repository interfaces, not GORM.
- Monetary values are integer cents.
- Dates crossing the API boundary use ISO 8601 strings.
- Database-specific queries remain inside repository implementations.

## Versioned database migrations

`database.Open` runs the SQL files embedded from `internal/database/migrations`
before the server accepts requests. Versions are consecutive integers starting
at 1 with no fixed digit width (001, 002, ... 999, 1000); the loader orders
files by numeric version, so growing past 999 needs no rename. `schema_migrations`
records the version, filename, SHA-256 of the exact SQL bytes, and UTC
application time. Applied files are immutable; append a new file for subsequent
changes. There is no runtime GORM AutoMigrate or model-driven schema diff. SQL
scripts must contain transaction-compatible SQLite DDL/DML without
BEGIN/COMMIT/ROLLBACK, VACUUM, or connection-level PRAGMA statements.

One SQLite `BEGIN IMMEDIATE` transaction serializes startup (five-second busy
wait) and commits all pending SQL and history rows together. An error rolls back
the pending batch and aborts startup; it never leaves half-applied changes. The
migration phase has a 30-second context timeout. Unknown/newer history, gaps,
renamed files or changed checksums abort startup rather than guessing a repair.

The early-development snapshots were squashed into a single `001_init.sql`
baseline. Databases created before that baseline (no migration history, or
history from the pre-squash 001-003 files) are not adopted: applying the
baseline fails and the batch rolls back, so startup aborts instead of silently
serving a mismatched schema. Development databases are recreated from `data/`;
real data is restored through a backup.

Operational steps and backup-based recovery are in [database-migrations.md](database-migrations.md).
