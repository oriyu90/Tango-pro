# Windows Runtime PoC report

## Environment

- Date: 2026-08-24
- Host: Windows 10 Pro 22H2 x64 (build 19045)
- Java: Eclipse Temurin 21.0.12
- Android SDK / Emulator: Android Emulator 37.1.11.0 (build 15917651), API 35 default x86_64 image
- Acceleration: WHPX (Windows Hypervisor Platform) confirmed usable after enablement and restart
- Guest: API 35, x86_64, 1536 MiB RAM, 2 vCPU
- scrcpy: 4.1 (release SHA-256 `5b12172b3264b2889f4583ee64752ce832e29bc8b1089dca81093459697165db`)

## Repository baseline

- Hygiene check: not run — `bash` is not available on this workstation.
- Version lockstep check: not run — `bash` is not available on this workstation.
- Site check: not run — Python launcher is present but no Python runtime is installed.
- Android test/lint/debug APK: passed with `ANDROID_HOME` and `ANDROID_SDK_ROOT` set to the local PoC SDK (`test`, `lint`, `stageDebugApk`; 58 tasks).

## Gate A — API 35 x86_64 Android guest

**Status: passed.**

After enabling Windows Hypervisor Platform and restarting, `emulator -accel-check` returned `WHPX(10.0.19045) is installed and usable`. The AVD booted successfully and reported `API 35` / `x86_64`.

The v2.1.0 debug APK installed successfully, `com.aistudio.vocabstudier.xwqnzy/com.example.MainActivity` became the resumed activity, and the dashboard rendered with the bundled Chinese book (460 words). Logcat contained no `UnsatisfiedLinkError` or fatal exception for Tango Pro.

## Gate B — Viewer virtual/flex display

**Status: passed.**

Normal scrcpy mirroring started with the Direct3D 11 renderer. A scrcpy virtual display was then created at 1280×960 / 240 dpi with `--new-display`, `--flex-display`, `--no-vd-system-decorations`, and direct start of Tango Pro. The viewer changed the Android virtual display from 1574×1558 to 2374×1558 when the Windows viewer window was resized, proving flex-display propagation.

## Gate C — TTS

**Status: passed for the API 35 PoC.**

The stock API 35 x86_64 image initially had no configured TTS engine (`tts_default_synth = null`), so Tango Pro logged `TextToSpeech Initialization Failed with status: -1`. An upstream eSpeak NG 1.52.0 release APK was installed for the PoC and configured as the guest's default engine. It is an offline engine and includes an x86_64 native library.

Android 11+ package visibility initially prevented Tango Pro from binding to the installed engine. With approval, the Android manifest received the generic `android.intent.action.TTS_SERVICE` `<queries>` declaration. This is an Android-wide compatibility declaration, not a Windows-specific path, and allows a user-selected installed TTS engine to be discovered on any supported Android host.

Tango Pro then bound successfully to `com.reecedunn.espeak.TtsService`. Existing-app UI tests invoked the question read-aloud control for all required language settings. The engine logged both voice selection and native synthesis for `en-us`, `cmn`, `fr-fr`, and `pt-br`. The eSpeak service also emitted a non-fatal `espeak_SetParameter: internal error` while applying parameters; synthesis still followed. Treat voice quality and that engine diagnostic as release-candidate items, not as a reason to bypass the production license and redistribution review.

### eSpeak NG candidate evaluation

eSpeak NG 1.52.0 was installed from the upstream release. Its APK contains `lib/x86_64/libttsespeak.so`, exposes `com.reecedunn.espeak.TtsService`, and initializes 141 bundled voices after first launch. The upstream source was pinned for PoC at commit `7d426728fe146f4168fa716e29d8e276c7da33f2`.

After installing the Windows C++ build tools, that pinned source checkout also built successfully with its Android Gradle project when MSVC was invoked in UTF-8 mode. The resulting debug APK identifies as eSpeak 1.53.0 / version code 23, contains `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native libraries, and has SHA-256 `3d38a96e22f84313b7da97271c68a79ad459376c9d739d71d9f88ba3209d901f`. It is a development artifact signed with the local Android debug certificate and is not a production payload.

The release APK is suitable only for this technical PoC. Production distribution still requires a binary-level license audit, a reproducible source build, package/signing decisions, and the formal audio-forwarding acceptance test. Google TTS is not bundled or used.

## Gate D — headless emulator

**Status: passed for the API 35 PoC.**

The same persistent AVD was restarted with `-no-window -no-boot-anim -gpu auto -no-snapshot`, without `-wipe-data`. It booted as API 35 / x86_64, retained the installed Tango Pro APK, and started successfully on a scrcpy virtual display. The host-visible surface was the viewer only; the Android emulator window was absent.

## Gate E — supervisor smoke test

**Status: passed for the local PoC stage.**

The Rust `TangoPro.exe` was built in release mode and run against a gitignored, local-only staging layout assembled from the PoC SDK. It completed `PREFLIGHT`, runtime/payload verification, dynamic paired-port allocation, persistent AVD preparation, headless emulator launch, ADB and boot waits, guest verification, safe APK update comparison, Tango start, and viewer start before reaching `RUNNING`.

A second invocation forwarded `GET_STATUS` through `\\.\pipe\TangoPro.Runtime.v1` and exited successfully instead of launching another runtime. A subsequent `SHUTDOWN` request exited successfully; the launcher log recorded `SHUTDOWN` then `EXIT`, and no launcher, viewer, or emulator process remained. No uninstall, downgrade, or data wipe was used.
