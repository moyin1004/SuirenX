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
- Schema changes use numbered SQL files in `services/api/internal/database/migrations`.
  Never call GORM `AutoMigrate` or modify an applied migration; append the next
  migration and test both upgrade and failure rollback.
- Archive is reversible and independent of lifecycle status. Archived assets
  are excluded from default lists and totals, remain readable, and must be
  restored before editing. Never auto-delete archived records.
- Runtime databases under `services/api/data` are local artifacts and must not
  be committed.

## Android rules

- Add dependencies and plugin versions to `gradle/libs.versions.toml`.
- Put shared Gradle configuration in `build-logic/convention`.
- Composable state flows down and events flow up. Public reusable composables
  take `modifier: Modifier = Modifier` as their first optional parameter.
- Collect `StateFlow` with `collectAsStateWithLifecycle()`.
- Launch ViewModel work in `viewModelScope`; rethrow `CancellationException`.
- Remote DTOs must be mapped into `core:model` types in the data layer.
- Cleartext HTTP is permitted only in the Debug manifest for local development.
- Do not add Room until offline source-of-truth and conflict behavior are
  explicitly designed.

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

## Change discipline

- Preserve user changes and avoid broad formatting-only rewrites.
- Do not commit secrets, signing keys, `local.properties`, generated build
  output, APKs, or local SQLite files.
- Update `docs/TODO.md` when completing or introducing meaningful work.
- Update this file when an architectural decision becomes a repository-wide
  invariant.
