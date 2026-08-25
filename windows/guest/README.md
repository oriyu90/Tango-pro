# Guest runtime boundary

This directory defines guest dependencies; it does not contain guest binaries. Bliss OS 16.9.7 and 15.9.2, and the Android 15 AOSP non-GMS x86_64 GSI system-only overlay, are rejected technical candidates. The GSI replaced only `system.img` in a development-only copy of the API 35 AEMU reference image and did not reach an online ADB or boot-complete state. None is a complete guest runtime or authorizes redistribution of reference kernel, vendor image, or AEMU package.

`SdkAemuGuest` remains the API 35 development and regression reference. `PrebuiltQemuGuest` is the candidate production backend and uses QEMU with WHPX, a read-only guest source, and a separately persistent `data.img`.

The candidate status is intentionally not approval. The GSI fetch script requires a user's explicit prior acceptance of the Android Developers GSI terms. Do not package the GSI, reference image, or extracted files, enable Guest Internet access, or mark any license entry approved until the documented acceptance and license gates are complete.
