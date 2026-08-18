# Tango pro repository guide

This repository contains the native Android and macOS implementations of Tango pro. The implementations stay separate; shared behavior is fixed by the design documents and cross-platform archive tests.

## Repository map

- `app/`: Android app (Kotlin, Jetpack Compose, Room)
- `macos/TangoProMac/`: macOS app (Swift, SwiftUI, JSON persistence)
- `docs/`: data contracts, development procedures, release checks, ADRs
- `testdata/fixtures/`: committed Android/macOS study-archive compatibility fixtures
- `scripts/`: repository, site, version, and cross-platform verification
- `DESIGN_DOC.md`: shared behavior and product rules
- `index.html`, `assets/`, `tokens.css`: public product site

## Read before changing behavior

1. `DESIGN_DOC.md`
2. `docs/CSV_FORMAT.md` for CSV parsing or export changes
3. `docs/STUDY_ARCHIVE_FORMAT.md` for backup/import changes
4. `docs/DEVELOPMENT.md` for the verification procedure

## Non-negotiable rules

- A user-visible behavior change that applies to both platforms must be implemented on Android and macOS, with `DESIGN_DOC.md` updated in the same change.
- Keep the native implementations separate. Do not introduce Kotlin Multiplatform without a new ADR and explicit approval.
- Preserve CSV and study-archive backward compatibility. Add or refresh fixtures when the archive contract intentionally changes.
- Do not change Android `versionName` / `versionCode` or macOS `CFBundleShortVersionString` / `CFBundleVersion` unless the user explicitly requests a version change.
- Never commit signing keys, passwords, tokens, `.env`, `local.properties`, APKs, DMGs, build output, or `.local-archive/`.
- Commit, tag, push, and GitHub Release operations require the user's explicit approval after the diff and verification results are shown.

## Verification

```bash
bash scripts/check_repository_hygiene.sh
bash scripts/check_version_lockstep.sh
python3 scripts/check_site.py
bash scripts/run_cross_platform_tests.sh
```

The cross-platform script runs Android tests/lint/build, the macOS core self-test, Android-to-macOS import, macOS-to-Android import, and committed-fixture compatibility checks.

## Contribution conventions

Use Conventional Commits with a scope where useful: `feat(android): ...`, `fix(macos): ...`, `test(archive): ...`, `docs(site): ...`, `chore(repo): ...`. See `CONTRIBUTING.md` and `docs/RELEASE_CHECKLIST.md` before proposing a commit or release.
