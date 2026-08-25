use std::path::PathBuf;

use thiserror::Error;

#[allow(dead_code)]
#[derive(Debug, Error)]
pub enum LauncherError {
    #[error("runtime component is missing: {0}")]
    MissingRuntime(PathBuf),
    #[error("runtime integrity check failed for {0}")]
    Integrity(PathBuf),
    #[error("APP_SIGNATURE_MISMATCH")]
    AppSignatureMismatch,
    #[error("RUNTIME_BOOTSTRAP_HASH_MISMATCH")]
    RuntimeBootstrapHashMismatch,
    #[error("RUNTIME_LICENSE_REQUIRED")]
    RuntimeLicenseRequired,
    #[error("RUNTIME_PACKAGE_UNAVAILABLE")]
    RuntimePackageUnavailable,
    #[error("RUNTIME_PACKAGE_REVISION_MISMATCH")]
    RuntimePackageRevisionMismatch,
    #[error("INSUFFICIENT_DISK_SPACE")]
    InsufficientDiskSpace,
    #[error("TTS_PROVISIONING_REQUIRED")]
    TtsProvisioningRequired,
    #[error("RUNTIME_SETUP_CANCELLED")]
    RuntimeSetupCancelled,
    #[error("RUNTIME_SETUP_INCOMPLETE")]
    RuntimeSetupIncomplete,
    #[error("installed application is newer than the bundled payload; downgrade is refused")]
    DowngradeRefused,
    #[error("GUEST_MANIFEST_INVALID")]
    GuestManifestInvalid,
    #[error("GUEST_DOWNLOAD_FAILED")]
    GuestDownloadFailed,
    #[error("GUEST_HASH_MISMATCH")]
    GuestHashMismatch,
    #[error("GUEST_ARCH_UNSUPPORTED")]
    GuestArchUnsupported,
    #[error("GUEST_BOOT_FAILED")]
    GuestBootFailed,
    #[error("GUEST_ADB_UNAVAILABLE")]
    GuestAdbUnavailable,
    #[error("GUEST_UNSUPPORTED")]
    GuestUnsupported,
    #[error("QEMU_START_FAILED")]
    QemuStartFailed,
    #[error("QEMU_WHPX_FAILED")]
    QemuWhpxFailed,
    #[error("HOST_CPU_TOO_OLD")]
    HostCpuTooOld,
}
