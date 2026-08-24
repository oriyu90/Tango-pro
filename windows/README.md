# Tango Runtime for Windows

This directory will contain a dedicated Windows runtime that runs the existing Tango Pro Android APK. It is not a separate Windows implementation of the app.

## Current state

Repository audit, app metadata locking, and the API 35 x86_64 PoC are complete. Gate A (guest compatibility), Gate B (virtual/flex viewer display), Gate C (offline TTS in all four supported languages), and Gate D (headless emulator plus viewer) passed for the documented local PoC. `POC_REPORT.md` records the evidence and remaining release work.

## Next prerequisite

The next implementation phase is the Rust supervisor in `windows/launcher`. The PoC bootstrap script remains available for a clean, explicitly licensed developer setup.

## Metadata lock

Generate the lock from the Android Gradle configuration:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\scripts\inspect-app.ps1
```

When a staged APK is available, pass `-ApkPath` as well. The script verifies that the APK identity matches the Gradle configuration before writing the lock.

See `ARCHITECTURE.md` and `DEVELOPMENT.md` for boundaries and the PoC procedure.
