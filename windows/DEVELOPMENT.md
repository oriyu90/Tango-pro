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

## 4. Official SDK First-Run runtime

The production installer contains no Google Android SDK, emulator, ADB, Command-line Tools, or system image. It includes `sdk-bootstrap.lock.json` and `runtime/setup-android-runtime.ps1` only. On first run, the launcher detects a fully matching SDK in this order: Tango's dedicated SDK, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, then the standard per-user Android SDK path. Existing SDKs are never modified.

When no ready Tango AVD exists, the setup dialog provides **利用規約を確認**, **セットアップ**, and **キャンセル**. Setup downloads the official Command-line Tools ZIP to a `.part` file, verifies the pinned SHA-256, then renames it. It runs `sdkmanager --licenses` visibly; no input is piped and no license is auto-accepted. After the user completes that step, it checks `sdkmanager --list`, requires the exact locked API 35 default x86_64 package revisions, installs them into the dedicated root only, and creates `TangoPro_API35_x86_64` beneath the Tango runtime directory.

For a distributable stage, use `stage-official-sdk-runtime.ps1`. Set `TANGO_APKSIGNER` to an externally installed build-tools `apksigner.bat`; the verification script rejects every packaged Android SDK directory.

## 5. Supervisor

The launcher is a Windows GUI-subsystem Rust binary in `windows/launcher`. It uses only runtime-relative executable paths, creates a named singleton mutex, reserves a paired emulator port, keeps child processes in a kill-on-close job, and never uninstalls, downgrades, or wipes guest data.

`TANGO_RUNTIME_ROOT` and `TANGO_USERDATA_ROOT` are development-only overrides for an isolated staged runtime. They are not installer settings.

## 6. Historical guest research

Do not resume Bliss, GSI, Android CI, Cuttlefish, QEMU, or other guest discovery from this procedure. The rejected and blocked evidence is retained in `POC_REPORT.md` and `windows/guest/research/` for audit only.
