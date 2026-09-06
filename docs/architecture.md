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

## Asset lifecycle

`PUT /api/v1/assets/:id/status` accepts `status` (`ACTIVE` / `RETIRED`) and
`retired_date`. Retiring requires a `YYYY-MM-DD` date between purchase day and
server-local today, inclusive. Reactivation requires an empty date and clears
the stored retirement value. Repeating a request yields the same lifecycle
state. Every asset response includes `retired_date`, empty while active.

Held days include both endpoints: purchase through today for active assets,
purchase through retirement for retired assets. Reactivation counts from the
original purchase date, including the intervening retired period; this version
does not maintain a history of service periods. The overview includes all asset
prices but sums daily costs only for active assets. Editing a retired asset
cannot move its purchase date beyond retirement.

## Reversible archive

Archive is independent of `ACTIVE` / `RETIRED`. `PUT /api/v1/assets/:id/archive`
requires `action: "ARCHIVE"` or `action: "RESTORE"`; retries preserve the first
archive timestamp. Responses always include `archived_at` (RFC 3339 when
archived, empty otherwise). No automatic expiration or permanent deletion is
implemented. Restoring preserves price, purchase date, service status and
retirement date; archiving does not pause the held-day calculation.

`GET /api/v1/assets` defaults to non-archived assets. `scope=CURRENT`,
`scope=ARCHIVED`, and `scope=ALL` select visibility independently of `status`.
Android loads `ALL` for local filters but excludes archived records from all
everyday filters and the overview. Archived detail is readable by ID and only
offers restoration; editing and lifecycle writes return HTTP 409 until restored.
The nullable archive column is introduced by versioned SQL migration 002.

## Asset icons

Asset visuals render from a bundled Material Symbols Rounded variable font,
subset to the curated glyphs and shipped as `res/font` in the shared `core/ui`
module. The public `MaterialSymbol` renders a private-use-area codepoint with
`BasicText` (font size derived from the dp icon size, so rendering is
independent of font scale); tint follows `LocalContentColor` like an
`ImageVector` icon, and `filled = true` selects a second Font entry that pins
the FILL axis to 1 (used for the selected navigation tab). The full variable
font is ~15 MB; the subset is ~65 KB for 16 glyphs. Regenerate it with
`apps/android/scripts/subset-material-symbols.sh` when adding glyphs. The app
does not depend on `material-icons-extended`; only the small `material-icons-core`
remains, for a handful of action icons (search, refresh, close, check, edit).

`icon_key` is a stable, language-neutral string shared through the API:
`devices`, `laptop`, `phone`, `tablet`, `headphones`, `watch`, `camera`,
`gamepad`, `book`, `keyboard`, `bicycle`, or `home`. The key-to-glyph mapping
lives only in the UI module; the server never stores glyphs. No uploaded image
files or image-serving infrastructure are needed. `image_url` remains in the
existing API for compatibility.

Creation defaults an empty icon to `devices`; updates with an omitted or empty
icon preserve the existing choice for older clients. An explicit `devices`
resets the choice. Unknown input keys return HTTP 400. Legacy database rows
render as `devices`; Android also renders a generic fallback for unknown keys
received from a newer server, preserving the raw key in the model.

Each category also carries a soft pastel `containerColor` / `contentColor`
pair, defined next to the key-to-glyph mapping in `feature/assets`'s
`AssetIcons.kt`. `AssetCategoryIcon` renders the glyph centered in a rounded
tinted tile on list cards, the detail header, and the form icon button. The
icon picker intentionally stays neutral (`surfaceVariant` tiles,
`secondaryContainer` only for the selected item), so color reads as a
property of the chosen asset rather than a selection affordance. The pairs
are tuned for the light-only theme; `icon_key` and the API remain unchanged.

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
