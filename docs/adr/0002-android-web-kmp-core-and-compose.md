# ADR 0002: Share Kotlin core and Compose UI between Android and Web

- Status: Accepted
- Date: 2026-08-24
- Supersedes: ADR 0001 for Android/Web sharing only

## Context

Tango pro v2.1.0 is implemented as a Kotlin/Jetpack Compose Android app and a
Swift/SwiftUI macOS app. A browser version is required at
`https://studio-rizi.pages.dev/projects/tango-pro/web/` while preserving the
Android behavior, CSV contract, and study-archive contract.

Room 3.0.1 and AndroidX SQLite 2.7.0 support JavaScript and WasmJS through
`WebWorkerSQLiteDriver`, SQLite Wasm, and OPFS. Compose Multiplatform 1.11.1
supports browsers with WasmGC. These releases make Android/Web sharing viable,
but the existing Android Room 2 database and the native macOS implementation
must not be put at risk by an all-at-once migration.

## Decision

- Introduce a Kotlin Multiplatform `shared` module for Android and WasmJS.
- Share platform-neutral models, learning rules, presentation state, and
  Compose UI between Android and Web in stages.
- Keep the Android application ID, Room 2.7.0 database, schema version 4,
  database name, columns, and migrations unchanged for the first Web release.
- Implement the Web database with Room 3.0.1 and SQLite 2.7.0 over
  `WebWorkerSQLiteDriver` and OPFS behind the same repository contract.
- Treat CSV and study archive ZIP files as the cross-platform persistence
  contract. Raw database files are not portable artifacts.
- Keep ZIP engines, file pickers, settings stores, TTS, sound, sharing, and
  other operating-system integrations platform-specific. Share their contracts
  and format validation rules.
- Keep the macOS Swift/SwiftUI implementation native. It remains compatible
  through the design documents and committed cross-platform fixtures.
- Publish a generated static bundle through the existing `oriyu90/studio-rizi`
  Cloudflare Pages project; do not create another Pages project.

## Consequences

- Android and Web can converge on one behavior and UI implementation without
  migrating the Android database during the Web project.
- Room entities and DAOs remain duplicated until a separately approved Android
  Room 3 migration.
- Compose Multiplatform 1.11.1 uses a pre-stable Material 3 component, so every
  UI extraction requires Android screenshot and interaction regression tests.
- The browser build requires COOP/COEP headers, OPFS capability checks,
  single-writer tab coordination, and a safe Service Worker update lifecycle.
- Rollback remains possible by reverting the Android app's dependency on the
  shared module; no Android database rollback is involved.

## Approval

The repository owner explicitly approved implementation from the reviewed
Web migration guide on 2026-08-24.
