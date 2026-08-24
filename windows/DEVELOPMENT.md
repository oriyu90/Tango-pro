# Development procedure

## 1. Create the app lock

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\scripts\inspect-app.ps1
```

This reads `app/build.gradle.kts`; it does not maintain a second manually edited version number.

## 2. Bootstrap a local PoC SDK

Download the Android Command-line Tools ZIP from the official Android Studio download page. Copy its SHA-256 from that page, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\scripts\bootstrap.ps1 `
  -CommandLineToolsZip C:\path\to\commandlinetools-win-..._latest.zip `
  -ExpectedSha256 <official-sha256> `
  -AcceptAndroidSdkLicense
```

The SDK is placed below `%LOCALAPPDATA%\TangoPro\poc-sdk` by default and is never committed. The script installs API 35 x86_64 dependencies only; API 36 is an explicit later comparison, not an automatic fallback.

## 3. PoC gates

Build `stageDebugApk`, create an API 35 default x86_64 AVD, boot it with WHPX, install the APK, and record every result in `POC_REPORT.md`. Do not start the supervisor until Gate A passes; do not start headless work until the four-language TTS Gate C passes.

## 4. Supervisor

The launcher is a Windows GUI-subsystem Rust binary in `windows/launcher`. It uses only runtime-relative executable paths, creates a named singleton mutex, reserves a paired emulator port, keeps child processes in a kill-on-close job, and never uninstalls, downgrades, or wipes guest data.

`TANGO_RUNTIME_ROOT` and `TANGO_USERDATA_ROOT` are development-only overrides for an isolated staged runtime. They are not installer settings.
