# License gate

The Android 15 AOSP x86_64 GSI was a technical candidate only. It was fetched from the official Android Developers page after explicit user acceptance of its terms, rather than bundled into the installer. Its rejected status does not authorize redistribution, modification, or an `approved` entry in `SOURCE_COMPONENTS.json`.

Before any offline bundle or production package, audit the exact GSI and every composed runtime component (AEMU, kernel, ramdisk, vendor, encryption key, ADB, viewer, and TTS), record their notices and source-offer obligations, and obtain an explicit approval in the license matrix.
