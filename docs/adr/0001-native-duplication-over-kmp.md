# ADR 0001: Prefer native duplication over Kotlin Multiplatform

- Status: Accepted
- Date: 2026-08-18

## Context

Tango pro has a Kotlin/Compose Android app and a Swift/SwiftUI macOS app. They share user-visible rules and exchange CSV and study-archive data, but use different UI frameworks, persistence mechanisms, and platform services such as TTS and file pickers.

## Decision

Keep the platform implementations native and separate. Define shared behavior in `DESIGN_DOC.md`, define exchanged data in `docs/CSV_FORMAT.md` and `docs/STUDY_ARCHIVE_FORMAT.md`, and enforce compatibility with Android/macOS round-trip tests and committed golden fixtures.

## Consequences

- Platform UI and integrations remain idiomatic and independently buildable.
- Shared behavior changes require deliberate implementation and review on both platforms.
- Contract tests, fixtures, and documentation are release-critical infrastructure.
- A future KMP migration requires evidence that it reduces total maintenance cost, an explicit migration plan, and a superseding ADR.
