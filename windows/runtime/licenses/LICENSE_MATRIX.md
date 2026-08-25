# Release license matrix

This file is intentionally incomplete until distributable binaries are selected. Do not mark a component approved solely because it was usable in the local PoC.

| Component | Version / revision | Source | License | Binary distributed | Source / notice obligation | Approval |
| --- | --- | --- | --- | --- | --- | --- |
| Google Android SDK / AEMU / API 35 default x86_64 image | Fixed by `sdk-bootstrap.lock.json` | Google Android SDK, fetched directly by the user | Google SDK terms | No | Tango does not redistribute; first-run setup shows the terms and the user accepts through SDK Manager | Legal review pending; implementation gates pending |
| Android 15 AOSP x86_64 GSI | AP3A.241005.015; SHA `96716f…300f9` | Android Developers GSI release | Android 15 GSI terms; explicit acceptance recorded for PoC | No | Never package; rejected by GSI-PG1 system-only boot gate | Rejected (technical gates) |
| API 35 reference kernel / vendor / ramdisk / encryption key | local API 35 default x86_64 PoC | Android SDK | Google SDK terms | No | Historical PoC reference only; not a separately distributed asset | PoC only |
| QEMU | 11.1.0 candidate | QEMU Community / Weilnetz Windows package | GPL-2.0; binary and notices audit pending | No | Pending audit | Pending |
| Bliss OS 16.9.7 FOSS Generic | 2024-10-11; SHA `735cb9…ba68e` | Bliss OS SourceForge archive | Mixed upstream components; audit pending | No | Never package while rejected | Rejected (technical gates) |
| Bliss OS 15.9.2 FOSS Generic | 2024-10-11; SHA `686d53…739b` | Bliss OS SourceForge archive | Mixed upstream components; audit pending | No | Never package while rejected | Rejected (technical gates) |
| scrcpy / TangoView | 4.1 / `2926c06` PoC baseline | Genymobile | Pending audit | No | Pending audit | Pending |
| eSpeak NG | Source `7d426728…`; debug PoC APK only | upstream source | GPL-3.0-or-later candidate; formal binary/voice-data audit pending | No | See `ESPEAK_NG_AUDIT.md`; Corresponding Source and notices plan required before any conveyance | Blocked: no approved release signing or legal review |
| Rust crates | `Cargo.lock` | crates.io | Pending audit | Yes | Pending audit | Pending |
| Inno Setup | 7.1.0 baseline | jrsoftware.org | Pending audit | No | Pending audit | Pending |
