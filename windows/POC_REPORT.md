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

## Prebuilt Guest candidate — Bliss OS 16.9.7 FOSS Generic

**Status: rejected.**

The archived upstream ISO `Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso` was downloaded outside Git and verified by three matching values: the upstream checksum artifact, the repository manifest, and `Get-FileHash`. The measured SHA-256 was `735cb962ec6bd92b62eb82a812831a38d79a0dfdf12b7973d2d0f7ab001ba68e` and the size was 2,342,518,784 bytes.

The actual ISO layout contains root-level `kernel`, `initrd.img`, and read-only `system.efs`; it does not contain the `AndroidOS/system.sfs` layout assumed by the original direct-extracted-boot proposal. The preparation script was updated to preserve and extract those real upstream boot assets without changing the system image.

QEMU 11.1.0 was obtained through the QEMU Community Windows package after its installer SHA-256 matched the pinned value. QEMU reported both `tcg` and `whpx` accelerators. WHPX initialization succeeded with a non-fatal performance-monitoring warning. CPU models were selected explicitly (`max`, then `Haswell-noTSX-IBRS`); `-cpu host` was not used.

Two visible/diagnostic boot configurations were evaluated with QEMU user networking restricted to a host-local ADB forward only:

- `virtio-vga` plus `-cpu max` kept the VM running and eventually exposed `127.0.0.1:5555` as an ADB `device`, but ADB shell and `getprop` never responded after several minutes. The process reached about 1.6 GiB working set and accumulated sustained CPU time.
- Standard VGA plus explicit `Haswell-noTSX-IBRS` remained running but QEMU framebuffer capture was fully black (`1280x800`) and did not reach a usable Android UI or ADB shell.

This fails the candidate requirements for observable boot/graphics (PG-1), automated responsive ADB (PG-2), viewer compatibility (PG-4), and initial performance. No Tango APK, TTS package, package disabling, data migration, or production provisioning was attempted on the rejected guest. The guest manifest and component lock record `rejected`; the production package guard remains closed.

## Prebuilt Guest fallback — Bliss OS 15.9.2 FOSS Generic

**Status: rejected.**

The upstream ISO `Bliss-v15.9.2-x86_64-OFFICIAL-foss-20241011.iso` was downloaded outside Git and verified against its upstream checksum and repository manifest. SHA-256 was `686d53f5f270ac2924430a88f9fb94085936a45fcff849044fade792fa39739b`; size was 2,019,557,376 bytes.

Unlike the Bliss 16 candidate, this ISO contains the expected root-level `kernel`, `initrd.img`, and `system.sfs` layout. The assets were extracted read-only. QEMU VFAT presentation was rejected by QEMU itself because the 1.9 GiB source directory exceeds its approximately 516 MiB FAT16 capacity. The documented alternative of presenting `system.sfs` as a read-only virtio disk and directly starting `kernel`/`initrd.img` was then attempted with WHPX, explicit non-host CPU model, restricted user networking, and no writable userdata image. The VM stopped immediately before reaching an observable Android frame, TCP ADB service, or serial diagnostic.

The normal ISO boot fallback also did not maintain a usable guest process in this host configuration. Consequently it cannot meet PG-1 boot, PG-2 responsive ADB, PG-4 viewer, PG-8 persistence, or PG-10 performance. No application, viewer, TTS, or provisioning operation was run. The fallback manifest and runtime component lock are `rejected`; no production artifact may be produced from either candidate.

## GuestBackend abstraction regression — SDK/AEMU

**Status: passed.**

After introducing `SdkAemuGuest` and `PrebuiltQemuGuest`, a new local-only SDK stage ran with `guestBackend: sdk-aemu`. The rebuilt launcher recorded `START`, `PREFLIGHT`, `VERIFY_RUNTIME`, `PREPARE_USERDATA`, `START_EMULATOR` (internally selected as `sdk-aemu`), `WAIT_ADB`, `WAIT_BOOT`, `VERIFY_GUEST`, `VERIFY_APP`, `UPDATE_APP`, `START_TANGO`, `START_VIEWER`, and `RUNNING`. This confirms that the existing API 35 AEMU reference path remains functional after the backend abstraction. It does not qualify the rejected QEMU candidates for distribution.

## AOSP CI guest candidate — Android 15 `sdk_phone_x86_64`

**Status: blocked before download; no candidate manifest or guest payload was created.**

The requested target cannot be selected as an Android 15/API 35 production candidate without changing the task requirements. The official AOSP change `4bf479f6057ad532c792e26d3d958a8b50fc1f02` (2023-10-30) retired `sdk_phone_x86_64` and states that the `sdk_phone*` targets were replaced by `sdk_phone64*`. It deleted `target/product/sdk_phone_x86_64.mk`, before Android 15 was released.

On 2026-08-25, the public Android CI dashboard's `aosp-android-latest-release` grid exposed current `aosp_cf_*` userdebug targets only, not `sdk_phone_x86_64`. Direct dashboard checks for both `android15-release` and `aosp-android15-release` displayed the dashboard's "Branch ... not found or your credential is expired" response. Consequently no actual public Android 15 `sdk_phone_x86_64` build ID, build date, artifact list, size, artifact URL, or SHA-256 could be established.

This blocks AC-1 through AC-7 and therefore all boot, identity, application, TTS, viewer, graphics, audio, performance, and approval gates for an `AospCiGuest`. No build ID has been guessed; no Android CI artifact was downloaded; no manifest with invented values was committed; and the existing successful `SdkAemuGuest` PoC was not altered. An alternative target such as `sdk_phone64_x86_64` would be a materially different guest and requires explicit user approval before evaluation.

## Android 15 AOSP x86_64 GSI overlay candidate

**Status: rejected at GSI-PG1 visible boot; downstream gates not run.**

The official Android Developers GSI release page was checked on 2026-08-25. Its Android 15 initial-release non-GMS x86_64 entry exactly matches build `AP3A.241005.015`, artifact `aosp_x86_64-exp-AP3A.241005.015-12366759-96716f9b.zip`, and SHA-256 `96716f9bff890f7336274b8bc80b3dd816b3571f881beffbfe91800ee41300f9`. The page also exposes newer Android 15 QPR1 and QPR2 non-GMS x86_64 artifacts, but no newer build is selected merely because it is newer; the initial API 35 release is the first candidate because it aligns with the existing API 35 reference environment.

This candidate is explicitly a composed technical runtime, not a complete guest image: an official GSI `system.img` would replace only `system.img` in a development-only copy of the successful API 35 AEMU reference image. The reference kernel, ramdisk, vendor, encryption key, AVD, and SDK package are neither changed nor approved for redistribution. The copied reference image audit is recorded at `windows/guest/research/sdk-api35-reference-image.json`.

After explicit user acceptance of the Android 15 GSI terms, the official artifact was downloaded to a gitignored cache and its SHA-256 matched the manifest. The archive contains `build.prop`, a 2,029,531,136-byte raw ext4 `system.img` (SHA-256 `06f332d8337a6c6639a4ecb5fc72902a92f221bc6d4966cb8aba693cca577829`), and `vbmeta.img`. The GSI `system.img` alone was substituted into a new development-only copy of the reference image; the original SDK image and AVD were not changed.

The GUI AEMU process started with `-gpu auto`; its log confirmed that WHPX was operational. It selected SwiftShader after detecting that the host Vulkan driver was below AEMU's required version. The guest's ADB serial remained `offline` for approximately four minutes and never produced online ADB, SystemUI confirmation, or `sys.boot_completed`. This fails GSI-PG1. The GSI-supplied `vbmeta.img`, or any other partition, was not substituted because that exceeds the defined `system.img`-only composition. No identity, GMS, ADB-command, Tango, TTS, audio, viewer, persistence, offline, performance, headless, supervisor, or distribution gate was run. The candidate is rejected, and the complete inspection is recorded at `windows/guest/research/android15-aosp-x86_64-gsi-inspection.json`.

## Official SDK First-Run implementation

**Status: Android runtime implementation complete; Production Release Candidate validation incomplete.**

The selected runtime model is now the existing successful API 35 default x86_64 AEMU reference, acquired by each user directly from the official Android SDK distribution rather than bundled by Tango Pro. `sdk-bootstrap.lock.json` pins the official Windows Command-line Tools bootstrap (`15859902`, SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`) plus the API 35 default x86_64 package IDs and the PoC-observed component revisions: Emulator `37.1.11`, platform-tools `37.0.1`, build-tools `36.0.0`, and system image revision `2`.

The first-run implementation downloads to `%LOCALAPPDATA%\TangoPro\runtime\android-sdk` only after the user opens the setup dialog and completes the visible `sdkmanager --licenses` interaction. It downloads to a `.part` file, verifies the pinned SHA-256 before rename, requires the exact package/revision rows to appear in `sdkmanager --list`, and stops rather than silently upgrading. On 2026-08-25, the local SDK Manager's actual package list contained all four locked IDs at their recorded revisions. It creates the separate `%LOCALAPPDATA%\TangoPro\runtime\avd\TangoPro_API35_x86_64.avd` with 1536 MiB, 2 vCPU, `-gpu auto`, cold boot, and no snapshots. Existing SDK roots are verified read-only and are never installation targets. The installer staging verifier rejects any bundled Android SDK directory.

No SDK license was accepted by automation while implementing this change. A clean first run is intentionally blocked with `TTS_PROVISIONING_REQUIRED` until eSpeak has an approved, reproducible release artifact and compliant provisioning path; the PoC debug APK is not eligible. The user-facing clean-host first-run, boot, safe-update, four-language real-audio, viewer, headless, offline, performance, legal-review, and release gates remain unclaimed. This implementation supersedes further guest discovery only; the historical Bliss, GSI, and Android CI rejected/blocked results above remain retained.

### Production RC static and fault-injection validation

**Status: partially passed; no clean-user license flow or production TTS gate claimed.**

On 2026-08-25, the Android Developers download page again listed `commandlinetools-win-15859902_latest.zip` and SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`; this exactly matches `sdk-bootstrap.lock.json`. The local PoC SDK Manager `--list` output contained each locked package/revision, and a read-only validator confirmed both `source.properties` and `package.xml` values: Emulator `37.1.11`, platform-tools `37.0.1`, build-tools `36.0.0`, and `system-images;android-35;default;x86_64` revision `2`.

The installed PoC component measurements were command-line tools 179,901,603 bytes, Emulator 1,082,790,554 bytes, platform-tools 17,548,112 bytes, build-tools 143,259,815 bytes, and system image 1,777,754,525 bytes. The bootstrap lock now derives a conservative fresh-install free-space threshold of 9,607,096,370 bytes from those measured components, explicit AVD/userdata and temporary allocations, plus a safety margin. A fault-injection run with zero available bytes recorded `BROKEN` / `INSUFFICIENT_DISK_SPACE` before any archive or `.part` file was created.

A separate test copied the real official Command-line Tools archive, changed one byte, and supplied it only to the test-mode bootstrap path. SHA-256 verification recorded `BROKEN` / `RUNTIME_BOOTSTRAP_HASH_MISMATCH`; neither the final archive nor its `.part` file remained in the runtime download directory. No SDK license was accepted, package was installed, AVD was created, application was started, or existing user data was modified by either fault-injection test.

A local staging audit assembled the release-mode `TangoPro.exe`, current Tango APK, bootstrap metadata/scripts, and a test-only TangoView fixture. The recursive staging verifier passed and found no `emulator.exe`, `adb.exe`, SDK Manager executable, system/vendor/userdata image, or Google SDK `package.xml`. This is a content-audit fixture only: the Tango APK is still debug-signed and no Setup.exe or GitHub release was produced.

The eSpeak audit verified clean source commit `7d426728fe146f4168fa716e29d8e276c7da33f2`, GPL version 3 license text, and a debug PoC APK with four native ABIs including x86_64. That APK is signed by `CN=Android Debug` and remains prohibited from production. A release-build attempt under the pinned SDK, UTF-8 Java mode, and Visual Studio Build Tools did not produce a release APK; the native `espeak-data` build stalled without an active compiler child process and was stopped. Production eSpeak provisioning, real audio, and all dependent clean-user gates remain blocked.
