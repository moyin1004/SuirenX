# SuirenX Agent Guide

These instructions apply to the entire repository.

## Product direction

SuirenX is a personal toolbox. The first vertical slice is native Android asset
tracking: record an asset, show its status, calculate held days, and calculate
cost per day. Android and iOS remain native applications. The server starts in
Go and may move performance-sensitive components to C++ behind stable API and
repository boundaries.

## Architecture invariants

- Keep this repository a monorepo until release cadence, permissions, or team
  ownership make a split necessary.
- Treat `api/proto` as the language-neutral public API contract. Do not expose
  GORM records or Android DTOs across that boundary.
- The public mobile transport is HTTP + JSON described by Protobuf IDL. Do not
  silently change it to gRPC or binary Protobuf.
- HTTP JSON uses snake_case, numeric integer cents, string statuses
  (`ACTIVE` / `RETIRED`), explicit zero values, and empty arrays. Do not switch
  to canonical protojson encoding. Regenerate Hertz code with
  `services/api/scripts/generate.sh`; do not hand-edit generated models/routes.
- Keep the Go server a modular monolith: transport -> service -> repository.
- Business services depend on repository interfaces, never directly on GORM.
- Keep Android dependencies flowing inward: app -> feature -> domain/model;
  data implements domain interfaces. Domain and model modules must not import
  `android.*`.
- Use Hilt for Android dependency injection, Retrofit for HTTP, `StateFlow` for
  screen state, and stateless Compose UI below route-level composables.

## Repository map

- `apps/android/app`: Android entry point and application wiring.
- `apps/android/feature/assets`: asset UI and ViewModel.
- `apps/android/core/model`: pure Kotlin domain values.
- `apps/android/core/domain`: repository interfaces and use cases.
- `apps/android/core/data`: Retrofit DTOs, mappings, and repository adapters.
- `apps/android/core/ui`: shared Compose theme and UI primitives.
- `api/proto`: Protobuf API definitions used by every client/server language.
- `services/api`: Hertz API, domain services, GORM repository, and SQLite.
- `build-logic`: Gradle convention plugins. Prefer changing shared Android
  configuration here instead of copying it into module build files.
- `docs`: architecture notes and the project task list.

## Data rules

- Asset visuals use built-in icons identified by stable `icon_key` strings.
  Render icon-library objects only in UI modules; do not add image upload/storage.
- Store money as integer cents (`int64`/`Long`), never floating point.
- Exchange date-only values as `YYYY-MM-DD` and timestamps as RFC 3339.
- An active asset's held days include both its purchase day and today.
- Retirement uses `retired_date` (`YYYY-MM-DD`, empty for active assets); held
  days include the retirement day. Retirement dates must be between purchase
  day and today. Reactivation clears the date and resumes counting from purchase.
- Keep SQLite-specific behavior in the database/migration infrastructure and
  GORM repository, outside business services.
- During pre-release development, keep the entire server SQL schema in the sole
  `services/api/internal/database/migrations/001_init.sql`; consolidate changes
  into 001 and do not add 002 or later files. Never call GORM `AutoMigrate`.
  Keep checksum validation and transactional rollback. Never automatically delete
  or rewrite an existing development database to bypass a baseline mismatch;
  back up/export data before explicitly rebuilding it. After the first production
  release, update this AGENTS.md rule to require immutable, append-only numbered
  migrations with upgrade and rollback tests before adding further migrations.
- Archive is reversible and independent of lifecycle status. Archived assets
  are excluded from default lists and totals, remain readable, and must be
  restored before editing. Never auto-delete archived records.
- Runtime databases under `services/api/data` are local artifacts and must not
  be committed.
- Android assets and expiry items always use the local Room database as the
  sole source of truth. The persisted legacy `StorageMode` controls only whether
  background synchronization is enabled; changing it never changes the UI data
  source. Every write/delete and its sync journal entry commit in one transaction.
- The server exposes authentication, incremental synchronization and health
  endpoints only, not asset/expiry CRUD. Use `m5/v1/m5.proto` to generate routes.
- Sync batches and idempotency keys are durable before HTTP; retries reuse the
  identical batch. Never hold a Room transaction across network I/O. Acknowledging
  an older revision must not overwrite a newer local edit. Conflicts pause only
  the affected record and preserve both versions until explicit resolution.
- A local dataset may synchronize to a different server/account after the user
  signs in and explicitly enables sync. Do not block on a previous target binding.
  Before changing targets, back up local data and old sync recovery metadata,
  then transactionally reset target-specific versions, cursors, batches and
  conflict state. Retain local records and tombstones; same-ID differences use
  explicit conflict resolution. Saving/selecting a server or logging in alone
  never starts uploading. Legacy cache enrollment remains account-scoped.
- Server configuration owns its account session. Store credentials per normalized
  server URL; logging into or out of a non-current server never changes the
  active sync account. Editing a URL replaces that entry and clears its old
  credential; renaming preserves it. Health probes never send credentials,
  save settings, or enable sync. Probe success means reachability only.
- Expiry item location is optional on both Android and the sync API; never invent
  placeholder business values to satisfy mismatched server validation.
- Deletion is explicit and separate from archive: remove the visible local row,
  retain a sync tombstone, and never garbage-collect tombstones without a device
  acknowledgement or full-resync protocol. Archive remains reversible.
- Local JSON backups are versioned, target the local Room database only,
  include archived records and expiry items, exclude credentials, and restore
  through validation plus an automatic pre-restore backup.

## Android rules

- Add dependencies and plugin versions to `gradle/libs.versions.toml`.
- Put shared Gradle configuration in `build-logic/convention`.
- Composable state flows down and events flow up. Public reusable composables
  take `modifier: Modifier = Modifier` as their first optional parameter.
- Collect `StateFlow` with `collectAsStateWithLifecycle()`.
- Launch ViewModel work in `viewModelScope`; rethrow `CancellationException`.
- Remote DTOs must be mapped into `core:model` types in the data layer.
- Cleartext HTTP is permitted only in the Debug manifest for local development.
- Room schema changes require explicit migrations; never reset an installed
  database as an upgrade strategy. See docs/data-sync.md for synchronization rules.

AGP 9 built-in Kotlin is temporarily disabled because the initial project uses
Hilt with kapt. The compatibility flags are intentionally visible in
`gradle.properties`; migrate to built-in Kotlin plus KSP before AGP 10.

## Go rules

- Run `gofmt` on changed Go packages.
- Wrap infrastructure errors with operation context.
- Validate input in the service layer so behavior is shared by all transports.
- Keep derived values such as held days and daily cost in the service layer.
- Add service unit tests for business rules and transport integration tests for
  each new endpoint.

## Verification

Run the relevant commands before handing off changes:

```shell
cd services/api && go test ./...
protoc -I api/proto -I "$(go env GOPATH)/pkg/mod/github.com/cloudwego/hertz/cmd/hz@v0.9.7/protobuf/api" \
  --descriptor_set_out=/tmp/suirenx-api.pb api/proto/suirenx/asset/v1/asset.proto
./gradlew :apps:android:app:assembleDebug
git diff --check
```

If the locally installed `hz` version changes, update the Protobuf include path
in the verification command instead of vendoring files from a module cache.

## Design and implementation consistency

- Use the current OpenDesign UI artifact as the reference for application UI.
- If the design differs from real data, domain rules, available capabilities,
  or required interaction states, update the OpenDesign artifact first. Only
  then implement the corresponding app/UI changes; never silently diverge in
  code or substitute fabricated fields, counts, timestamps, or status values.
- Keep the updated design and application consistent. If OpenDesign is blocked,
  record the discrepancy and leave dependent UI work pending; continue unrelated
  authorized work. Do not add an approval step unless the user requested one.

## Change discipline

- Preserve user changes and avoid broad formatting-only rewrites.
- Do not commit secrets, signing keys, `local.properties`, generated build
  output, APKs, or local SQLite files.
- Update `docs/TODO.md` when completing or introducing meaningful work.
- Update this file when an architectural decision becomes a repository-wide
  invariant.
