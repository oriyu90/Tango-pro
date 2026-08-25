# Tango Runtime for Windows

This directory will contain a dedicated Windows runtime that runs the existing Tango Pro Android APK. It is not a separate Windows implementation of the app.

## Current state

Repository audit, app metadata locking, and the API 35 x86_64 PoC are complete. Gate A (guest compatibility), Gate B (virtual/flex viewer display), Gate C (offline TTS in all four supported languages), and Gate D (headless emulator plus viewer) passed for the documented local PoC. `POC_REPORT.md` records the evidence and remaining release work.

## Current implementation

The Rust supervisor now implements an Official SDK First-Run path. It does not package Google Android SDK components. A user who elects setup downloads the locked official components into their own `%LOCALAPPDATA%\TangoPro\runtime\android-sdk` root and accepts the SDK terms themselves. See `ARCHITECTURE.md`, `DEVELOPMENT.md`, and `POC_REPORT.md` for the implementation boundary and gates that are still pending.

Android Studio is not required. Internet access is required only for first-run component acquisition or runtime repair. The supported baseline is Windows 10 22H2 x64 with WHPX enabled; normal runtime operation uses the fixed API 35 default x86_64 AVD and does not auto-update its Google components.

## Metadata lock

Generate the lock from the Android Gradle configuration:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\scripts\inspect-app.ps1
```

When a staged APK is available, pass `-ApkPath` as well. The script verifies that the APK identity matches the Gradle configuration before writing the lock.

See `ARCHITECTURE.md` and `DEVELOPMENT.md` for boundaries and the PoC procedure.
