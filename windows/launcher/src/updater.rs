use anyhow::Result;

use crate::{
    adb::Adb,
    app,
    errors::LauncherError,
    integrity::{self, AppLock},
    paths::Paths,
};

pub fn update_if_needed(adb: &Adb, paths: &Paths, lock: &AppLock) -> Result<()> {
    let installed = app::installed_version(adb, &lock.package_name)?;
    if installed.is_some() {
        verify_installed_signature(adb, paths, lock)?;
    }
    match installed {
        None => adb.install_replace(&paths.payload_apk()),
        Some(installed) if installed < lock.version_code => {
            adb.install_replace(&paths.payload_apk())
        }
        Some(installed) if installed == lock.version_code => Ok(()),
        Some(_) => Err(LauncherError::DowngradeRefused.into()),
    }
}

fn verify_installed_signature(adb: &Adb, paths: &Paths, lock: &AppLock) -> Result<()> {
    let remote = app::installed_base_apk(adb, &lock.package_name)?
        .ok_or(LauncherError::AppSignatureMismatch)?;
    let local = paths.state().join("installed-base.apk");
    adb.pull(&remote, &local)?;
    integrity::verify_apk_certificate(paths, &local, &lock.certificate_sha256)
}
