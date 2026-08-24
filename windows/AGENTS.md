# Windows Runtime contribution guide

The Windows implementation is a runtime for the existing Android APK. It must not reimplement Tango Pro learning logic, CSV parsing, Study Archive handling, Room data, Compose UI, or TTS timing.

## Gate order

Do not add or modify `windows/launcher/`, `windows/viewer/`, `windows/bridge/`, custom AEMU, guest images, or installer code until `windows/POC_REPORT.md` records a passing Gate A. Gate A requires the existing APK to install and start on an API 35 x86_64 Android Emulator without native-library, fatal-exception, or Compose-rendering failures.

## Data safety

- Keep distributable runtime files separate from `%LOCALAPPDATA%\\TangoPro` userdata.
- Never automate `adb uninstall` or `-wipe-data` as an update/recovery action.
- Never downgrade an installed APK.
- Do not parse or transform CSV or Study Archive files on the Windows side; transport them only.
- Keep generated APKs, SDKs, AVDs, emulator images, binaries, secrets, and logs out of Git.

## Required checks

- Run `powershell -ExecutionPolicy Bypass -File .\\windows\\scripts\\inspect-app.ps1` after any Android app metadata change.
- Run the existing repository checks described in `AGENTS.md` when their required tools are available.
- Record all PoC results, including blocked checks, in `windows/POC_REPORT.md`.
