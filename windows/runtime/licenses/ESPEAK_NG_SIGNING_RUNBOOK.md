# eSpeak NG production-signing runbook

Status: **not executable until the unsigned reproducibility and GPL gates pass**.

## Separation of duties

The unsigned build workflow has no production key, password, or signing task. An authorized release operator must run signing in a protected environment that is separate from ordinary CI. The signer must use an eSpeak-specific Android signing identity, not the Tango Pro application identity.

## One-time identity creation and custody

An authorized operator creates the initial identity outside this repository and keeps its private key only in an approved protected-secret or hardware-backed store. The private key, its password, and any encoded keystore must never be committed, attached to an issue, or exposed to the normal build workflow.

Before the first signed distribution, record in the protected release record: certificate SHA-256, non-secret alias identifier, creation date, designated custodians, and backup/recovery policy. Reuse this same certificate for every future eSpeak update; changing it breaks `adb install -r` upgrades.

## Per-candidate signing gate

1. Select the unsigned APK only after the two-build SHA-256 comparison passes.
2. Record its source commit, version name/code, unsigned SHA-256, lock file, and empty patch list (or the exact reviewed patch list).
3. Sign only in the protected signing environment.
4. Run `zipalign -c` and `apksigner verify --verbose --print-certs` on the signed APK.
5. Inspect the signed APK again for package, SDK levels, `x86_64`, `lib/x86_64/libttsespeak.so`, `espeakdata.zip`, and `espeakdata_version`.
6. Only then record signed SHA-256, certificate SHA-256, and ABI list in `windows/runtime/components.lock.json`.

No current candidate satisfies these gates, so this repository intentionally contains no signing command, signing secret, certificate fingerprint, or signed artifact.
