use std::path::PathBuf;

use thiserror::Error;

#[derive(Debug, Error)]
pub enum LauncherError {
    #[error("runtime component is missing: {0}")]
    MissingRuntime(PathBuf),
    #[error("runtime integrity check failed for {0}")]
    Integrity(PathBuf),
    #[error("APP_SIGNATURE_MISMATCH")]
    AppSignatureMismatch,
    #[error("installed application is newer than the bundled payload; downgrade is refused")]
    DowngradeRefused,
}
