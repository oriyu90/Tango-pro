# Architecture

Tango Pro for Windows has one application implementation: the Android APK.

```text
TangoPro.exe
  -> private AEMU + WHPX
  -> private ADB
  -> Tango Pro Android APK
  -> TangoView (scrcpy-based viewer)
```

The API 35 x86_64 PoC has passed its guest, viewer, TTS, and headless gates. The Rust supervisor is now the active implementation phase. The bridge, custom guest image, and installer remain independently gated: they must not duplicate Android learning logic or overwrite persistent user data.

Program files will be replaceable runtime assets. User data will always be isolated under `%LOCALAPPDATA%\TangoPro` and must survive runtime and APK updates.
