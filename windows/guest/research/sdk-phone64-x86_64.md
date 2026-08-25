# `sdk_phone64_x86_64` research

- Date checked: 2026-08-25
- Candidate state: **BLOCKED — no candidate manifest was created**
- Scope: upstream definition, Android 15/API 35 public binary availability, artifact suitability, and native ABI compatibility.
- Non-goals: no AOSP source checkout/build, no guessed build ID or artifact URL, no SDK Manager image adoption, and no changes to `SdkAemuGuest`.

## SP64-1 — upstream existence

Android 15's public source tag `android-15.0.0_r1` registers the following product in [`device/generic/goldfish/AndroidProducts.mk`](https://android.googlesource.com/device/generic/goldfish/+/refs/tags/android-15.0.0_r1/AndroidProducts.mk):

```make
$(LOCAL_DIR)/64bitonly/product/sdk_phone64_x86_64.mk
```

The corresponding [`sdk_phone64_x86_64.mk`](https://android.googlesource.com/device/generic/goldfish/+/refs/tags/android-15.0.0_r1/64bitonly/product/sdk_phone64_x86_64.mk) defines:

| Field | Recorded upstream value |
| --- | --- |
| Product | `sdk_phone64_x86_64` |
| `PRODUCT_DEVICE` | `emu64x` |
| Model | `Android SDK built for x86_64` |
| Product composition | `core_64_bit_only.mk`, `board/emu64x/details.mk`, and `product/phone.mk` |
| Partition model | dynamic partitions; 1536 MiB emulator dynamic partition size plus 8 MiB super-partition overhead |
| System-image metadata template | `development/sys-img/images_x86_64_source.prop_template` |

[`board/emu64x/BoardConfig.mk`](https://android.googlesource.com/device/generic/goldfish/+/refs/tags/android-15.0.0_r1/board/emu64x/BoardConfig.mk) records the emulator architecture as `TARGET_CPU_ABI=x86_64`, `TARGET_ARCH=x86_64`, `TARGET_ARCH_VARIANT=x86_64`, and `TARGET_2ND_ARCH_VARIANT=x86_64`. It is the AEMU/goldfish emulator configuration; it is not a Cuttlefish or physical-device product.

The relevant migration is the official platform/build change [`4bf479f6057ad532c792e26d3d958a8b50fc1f02`](https://android.googlesource.com/platform/build/+/4bf479f6057ad532c792e26d3d958a8b50fc1f02) (2023-10-30), **Retire obsolete emulator targets and boards**. It deleted `target/product/sdk_phone_x86_64.mk` and states that the `sdk_phone*` targets are replaced by `sdk_phone64*`. Thus `sdk_phone64_x86_64` is an upstream-defined 64-bit-only successor, not a spelling variant of the blocked target.

## SP64-2 — Android 15/API 35 binary availability

**Result: BLOCKED.**

No public, downloadable Android 15/API 35 build could be established with all mandatory factual fields:

- branch
- exact `sdk_phone64_x86_64` target and variant
- build ID and build date
- artifact list
- downloadable artifact URLs and reproducible SHA-256 values

The public Android CI dashboard was checked on 2026-08-25. Its accessible `aosp-android-latest-release` grid exposed only current `aosp_cf_*` userdebug targets (`aosp_cf_arm64_auto`, `aosp_cf_arm64_only_phone`, `aosp_cf_riscv64_phone`, `aosp_cf_x86_64_auto`, and `aosp_cf_x86_64_only_phone`); it did not list `sdk_phone64_x86_64`. Direct dashboard checks for `android15-release` and `aosp-android15-release` returned the dashboard's "Branch ... not found or your credential is expired" response. No rule-based dashboard URL or numerical build ID was generated after those results.

The Android 15 source tag above proves product-source existence, but is not a published Windows-consumable image artifact. Targeted checks of official Android CI, `android.googlesource.com`, and `dl.google.com/android/repository` did not return a public Android 15 `sdk_phone64_x86_64` binary inventory. Therefore there is no eligible artifact to download or hash.

## SP64-3 — artifact suitability

**Not evaluated: no actual artifact exists in the candidate record.**

The successful local API 35 SDK reference image remains a development/PoC input only. Its package metadata identifies it as `system-images;android-35;default;x86_64` (tag `default`, revision 2), not an Android CI `sdk_phone64_x86_64` artifact. It will not be copied, repackaged, or recorded as a production guest candidate.

Its observed AEMU image interface is retained solely as a future comparison checklist: `kernel-ranchu`, `ramdisk.img`, `system.img`, `vendor.img`, `encryptionkey.img`, `build.prop`, `kernel_cmdline.txt`, `VerifiedBootParams.textproto`, and a small `data/` template tree. A real candidate must supply and pin its own kernel, ramdisk, system/vendor/product partitions where applicable, userdata template, encryption key, hardware configuration, and emulator metadata before AEMU compatibility can be evaluated.

## SP64-4 — native ABI compatibility

The stated 64-bit-only architecture is compatible with the currently validated application payloads:

| Payload checked | x86_64 native entry | Result |
| --- | --- | --- |
| Tango Pro PoC APK | `lib/x86_64/libandroidx.graphics.path.so` | present |
| Upstream eSpeak NG 1.52.0 PoC APK | `lib/x86_64/libttsespeak.so` | present |

This is a package-layout check only, not a guest boot or audio acceptance test. Neither 32-bit x86 nor ARM compatibility is required for this candidate. The eSpeak package remains subject to the existing production license, reproducibility, and audio-forwarding gates.

## SP64-5 — decision

The upstream product is real and architecturally suitable for AEMU investigation, but the required public Android 15/API 35 binary facts are unavailable. Under the prescribed decision rule, no `AospCiPhone64Guest` manifest, fetcher, guest backend, artifact cache, boot attempt, or production bundle has been created.

Work can resume only with either a publicly accessible official artifact page that exposes the required exact fields, or authorized access to an Android CI build that exposes them. The existing `SdkAemuGuest` and the rejected Bliss 16/15 records remain unchanged.
