# eSpeak NG distribution and signing plan

Status: **planning only — no production eSpeak APK has been approved or distributed**.

## Corresponding Source plan

Every distributed eSpeak NG APK must have a corresponding-source record outside this Git repository. The record must include the exact upstream repository and commit, every Tango-authored patch (if any), the build lock at `windows/runtime/espeak-android-build.lock.json`, the unsigned build logs, and these build instructions. Large source archives must be release-infrastructure artifacts (for example `third-party-source/espeak-ng-<commit>.tar.zst`) and must not be committed to this repository.

The release review must also provide the GPLv3 text, applicable copyright notices, and a user-accessible source offer/location. This plan is not a legal conclusion; a license review must approve the completed package before distribution.

## Signing policy

The normal GitHub Actions workflow creates only `espeak-release-unsigned.apk` and has no access to signing keys or passwords. It runs only after an authorized operator uses the protected `espeak-production-build` environment, explicitly sets the `accept_android_sdk_terms` dispatch input to `true`, and provides the `ANDROID_SDK_LICENSES_TAR_GZ_B64` secret containing manually accepted Android SDK license records. The archive contains the contents of `licenses/`; CI extracts them into its Android SDK `licenses/` directory without restoring ownership, file modes, timestamps, or metadata of the pre-existing destination directory. The workflow never accepts SDK terms itself.

A separately reviewed secure release procedure may use an eSpeak-specific signing identity. That procedure must create the identity outside the repository, keep the private key in an approved protected secret or hardware-backed store, and record its certificate SHA-256, non-secret alias identifier, creation date, and backup/recovery policy. Once a production eSpeak APK is issued, subsequent updates must retain its signing certificate so `adb install -r` remains possible. The signed output must pass `zipalign -c` and `apksigner verify --verbose --print-certs` before its metadata is pinned.

The required signing separation and candidate checks are specified in `ESPEAK_NG_SIGNING_RUNBOOK.md`; it deliberately does not contain a signing key, secret, or runnable normal-CI signing command.

Only after a signed candidate exists may `components.lock.json` record its signed SHA-256 and certificate SHA-256. Until then, `TTS_PROVISIONING_REQUIRED` remains mandatory and debug APKs are not a fallback.
