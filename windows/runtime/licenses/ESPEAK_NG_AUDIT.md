# eSpeak NG production-candidate audit

Status: **not approved for production distribution**.

## Verified PoC inputs

- Source checkout: upstream eSpeak NG commit `7d426728fe146f4168fa716e29d8e276c7da33f2`; the checkout was clean when inspected on 2026-08-25.
- License file: `COPYING` identifies GNU GPL version 3. The production license classification must remain GPL-3.0-or-later pending formal review of the complete source tree and generated voice-data inputs.
- Android project: `android/build.gradle` declares version name `1.53.0`, version code `23`, Android namespace `com.reecedunn.espeak`, and CMake targets `ttsespeak` and `espeak-data`.
- PoC artifact: `android/build/outputs/apk/debug/espeak-debug.apk`, 17,139,260 bytes, SHA-256 `3d38a96e22f84313b7da97271c68a79ad459376c9d739d71d9f88ba3209d901f`.
- Native ABI inspection: `lib/arm64-v8a/libttsespeak.so`, `lib/armeabi-v7a/libttsespeak.so`, `lib/x86/libttsespeak.so`, and `lib/x86_64/libttsespeak.so` are present. The required `x86_64` ABI is therefore present in this PoC artifact.
- Signing inspection: the artifact is signed by `CN=Android Debug`; certificate SHA-256 `8042e71f7c87e94a9e785d8de6c18bd8f5601ef22165defd9f7bd3a1e2995ead`.

## Production blockers

The inspected APK is a debug artifact and must never be included in a Tango Pro release. A production candidate requires all of the following before it can be staged:

1. An approved product-specific signing identity and key-handling process.
2. A clean release build from the pinned source revision, with repeatable build inputs recorded.
3. APK version name/code, SHA-256, certificate SHA-256, and ABI list recorded from that release artifact.
4. A binary-level GPL and voice-data license review, complete notices, and a compliant Corresponding Source availability plan.
5. First-run provisioning design that does not install or redistribute this component until the preceding review is approved.
6. Real speaker acceptance for en-US, zh-CN, fr-FR, and pt-BR through both Tango manual and automatic speech paths.

## Release-build attempt

On 2026-08-25, `assembleRelease --no-daemon` was attempted from the pinned clean source using the local API 35 SDK, Java UTF-8 mode, and the Visual Studio 2022 Build Tools x64 development environment. No release APK was produced. The initial attempt correctly failed without an Android SDK environment; the corrected attempt reached native `RelWithDebInfo` configuration but stopped while building the `espeak-data` CMake target without producing an APK. The verified build Java/Ninja processes were then stopped after they no longer had child compiler processes or output progress. This is a reproducibility blocker, not permission to substitute the debug APK or to relax signing requirements.

No production signing key was created, selected, or used by this audit.

## Linux CI build route

The only planned release-build route is `.github/workflows/espeak-android.yml`. It clones the pinned upstream source into the CI workspace, verifies the exact commit, and runs the upstream command `cd android && ./gradlew --no-daemon --console=plain assembleRelease` twice without an upstream patch. Its fixed inputs are recorded in `../espeak-android-build.lock.json`.

The workflow is manual-only and refuses to begin unless an authorized operator explicitly records Android SDK terms acceptance in the dispatch form and supplies protected manually accepted license records. It does not run `sdkmanager --licenses` or otherwise accept the terms itself. It then produces only unsigned APK candidates and diagnostics; it has no signing credentials. On every failure it uploads the complete Gradle logs plus native `espeak-data` outputs, including `generated/espeak-ng-data`, CMake intermediates, `espeakdata.zip`, and `espeakdata_version` when present. A failing build must be diagnosed before an explicit patch is proposed.

The subsequent corresponding-source and signing requirements are documented in `ESPEAK_NG_DISTRIBUTION_PLAN.md`. No signed candidate metadata has been recorded in `components.lock.json` because no signed candidate exists.
