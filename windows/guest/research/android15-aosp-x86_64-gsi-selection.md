# Android 15 AOSP x86_64 GSI overlay selection

- Checked: 2026-08-25
- Official source: <https://developer.android.com/topic/generic-system-image/releases>
- Decision: **initial release was selected as the first technical candidate and is now rejected by the system-only visible-boot gate.**

## Candidate comparison

| Android 15 release | Build ID | Official page date / security patch | Non-GMS x86_64 artifact | SHA-256 | Selection result |
| --- | --- | --- | --- | --- | --- |
| Initial | `AP3A.241005.015` | 2024-10-15 / October 2024 | `aosp_x86_64-exp-AP3A.241005.015-12366759-96716f9b.zip` | `96716f9bff890f7336274b8bc80b3dd816b3571f881beffbfe91800ee41300f9` | Rejected by GSI-PG1 |
| QPR1 | `AP4A.241205.013` | 2024-12-05 / December 2024 | `aosp_x86_64-exp-AP4A.241205.013-12621605-422944cb.zip` | `422944cbefde67e232c1acb486d27c7f72ce63954a58b48141f7bb2b37baccf7` | Available, not first |
| QPR2 | `BP1A.250405.005.C1` | page lists April 2024 / April 2024 | `aosp_x86_64-exp-BP1A.250405.005.C1-13151952-12978bf9.zip` | `12978bf94c1e8dfee598941641df2aa73d29a0f967cc00f4f36b87ac1ed77bf2` | Available, not first; page date is retained verbatim because it is inconsistent with the build identifier |

All three entries are the `aosp_x86_64` row, not the adjacent `gsi_gms_x86_64` row. The candidate manifest pins the initial release because it is Android 15/API 35 and has the closest release alignment to the already passing API 35 AEMU reference environment. This is a test-order decision, not a claim that it is a superior or redistributable production runtime.

## Distribution and test boundaries

The official page states that Android GSI is intended for developer validation and cannot be redistributed except as expressly allowed by the individual download's terms. It also requires agreement to those terms before download. The terms were explicitly accepted for this PoC. Consequently:

- the initial GSI was downloaded and only `system.img` was extracted to a gitignored development stage; it was not mounted, modified, or repacked;
- the `fetch-aosp-gsi.ps1` guard requires an explicit `-AcceptAndroid15GsiLicense` switch after a human reviews and accepts the official terms;
- the local API 35 SDK image remains a non-distributable development reference and is not an input to a production package;
- the first technical test will use only a separate development copy and replace only its `system.img`.

The official release notes also record GSI-specific reboot and dynamic-system-partition caveats. They do not authorize a userdata wipe, deletion of product partitions, or modification of the successful reference AVD. The initial system-only overlay did not reach online ADB or boot completion, and no variant involving its `vbmeta.img` or other partition was attempted.
