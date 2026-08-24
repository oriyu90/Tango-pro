# Windows Runtime release checklist

## Before packaging

- [ ] `windows/POC_REPORT.md` records a passing Gate A through Gate D on Windows 10 22H2 x64.
- [ ] `windows/launcher` builds with its pinned Rust toolchain and its tests pass.
- [ ] The Android test, lint, and staged APK build pass.
- [ ] `windows/scripts/inspect-app.ps1 -ApkPath` refreshed `runtime/app.lock.json`.
- [ ] The production payload APK is signed with the approved update certificate.
- [ ] `apksigner verify --verbose --print-certs` matches the recorded certificate digest.
- [ ] The runtime contains self-built, license-reviewed AEMU/AOSP components; no SDK Manager binary or Google system image is copied into a distributable.
- [ ] `runtime.lock.json` and `components.lock.json` contain final immutable hashes.
- [ ] `runtime/licenses/` contains notices, source-component inventory, and license matrix.

## Acceptance

- [ ] A clean Windows 10 22H2 x64 host passes preflight with WHPX available.
- [ ] No emulator window or Android launcher is user-visible; TangoView alone is visible.
- [ ] CSV open uses transport only and reaches Tango Pro's existing import UI.
- [ ] Installed apps with a newer version or a different certificate are refused without uninstalling or downgrading.
- [ ] en-US, zh-CN, fr-FR, and pt-BR read-aloud work with the selected licensed offline engine.
- [ ] Closing Tango Pro leaves no emulator, viewer, ADB, or bridge orphan process.
- [ ] Upgrade retains `%LOCALAPPDATA%\TangoPro\data`; uninstallation leaves it by default.
