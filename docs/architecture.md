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
