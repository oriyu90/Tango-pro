# Official SDK Acquired Runtime architecture

Tango Pro for Windows has one application implementation: the Android APK.

```text
TangoPro.exe
  -> official SDK acquisition / existing-SDK read-only verification
  -> SdkAemuGuest
  -> private ADB
  -> Tango Pro Android APK
  -> TangoView (scrcpy-based viewer)
```

The API 35 x86_64 AEMU PoC is the fixed technical reference. Production acquisition is an Official SDK First-Run model: Tango Pro never bundles Google Android SDK, Emulator, or system-image files. After the user reviews the official terms and accepts them in SDK Manager, the locked components are downloaded directly from Google into `%LOCALAPPDATA%\TangoPro\runtime\android-sdk` (or `$TANGO_DEV_ROOT\android-sdk` for development). Existing SDKs are detected in the documented order and verified read-only; Tango Pro never installs into them.

`sdk-bootstrap.lock.json` is the versioned source of package IDs, revisions, bootstrap URL, and hash. The setup process must first inspect `sdkmanager --list`; if any exact locked package/revision is unavailable, it stops with `RUNTIME_PACKAGE_UNAVAILABLE` and does not upgrade automatically. The dedicated AVD is `%LOCALAPPDATA%\TangoPro\runtime\avd\TangoPro_API35_x86_64.avd`, configured for 1536 MiB, 2 vCPU, `-gpu auto`, and cold boot/no snapshot. Setup and repair touch only this third-party runtime/configuration area, never Tango learning data.

Program files contain Tango code, viewer assets, the setup script, and immutable lock metadata only. User data remains isolated under `%LOCALAPPDATA%\TangoPro` and survives updates. Historical Bliss, GSI, and AOSP CI research remains recorded under `windows/guest/`; it is not a fallback or an active production candidate.
