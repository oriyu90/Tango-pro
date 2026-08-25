# Windows Runtime release checklist

## Before packaging

- [ ] `windows/POC_REPORT.md` records a passing Gate A through Gate D on Windows 10 22H2 x64.
- [ ] `windows/launcher` builds with its pinned Rust toolchain and its tests pass.
- [ ] The Android test, lint, and staged APK build pass.
- [ ] `windows/scripts/inspect-app.ps1 -ApkPath` refreshed `runtime/app.lock.json`.
- [ ] The production payload APK is signed with the approved update certificate.
- [ ] `apksigner verify --verbose --print-certs` matches the recorded certificate digest.
- [ ] The installer contains no Google Android SDK, AEMU, ADB, Command-line Tools, or system image; `verify-runtime.ps1` passes using an external `apksigner`.
- [ ] `sdk-bootstrap.lock.json` contains the final official URL, package IDs, exact revisions, and SHA-256; a newer SDK package has not been auto-substituted.
- [ ] `runtime/licenses/` contains notices, source-component inventory, and license matrix.

## Acceptance

- [ ] A clean Windows 10 22H2 x64 host passes preflight with WHPX available.
- [ ] On a clean host, the user completes the visible SDK terms step; download hash verification, `sdkmanager --list`, package installation, and dedicated AVD creation pass.
- [ ] No emulator window or Android launcher is user-visible; TangoView alone is visible.
- [ ] CSV open uses transport only and reaches Tango Pro's existing import UI.
- [ ] Installed apps with a newer version or a different certificate are refused without uninstalling or downgrading.
- [ ] en-US, zh-CN, fr-FR, and pt-BR read-aloud work with the selected licensed offline engine.
- [ ] Closing Tango Pro leaves no emulator, viewer, ADB, or bridge orphan process.
- [ ] Upgrade retains `%LOCALAPPDATA%\TangoPro\data`; uninstallation leaves it by default.
