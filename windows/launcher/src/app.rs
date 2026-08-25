use anyhow::Result;

use crate::{adb::Adb, errors::LauncherError};

pub fn is_running(adb: &Adb, package_name: &str) -> bool {
    adb.command(&["shell", "pidof", package_name])
        .is_ok_and(|output| output.status.success() && !output.stdout.is_empty())
}

pub fn require_offline_tts(adb: &Adb) -> Result<()> {
    const ESPEAK_PACKAGE: &str = "com.reecedunn.espeak";
    let configured = adb.shell(&["settings", "get", "secure", "tts_default_synth"])?;
    if !configured.starts_with(ESPEAK_PACKAGE) {
        return Err(LauncherError::TtsProvisioningRequired.into());
    }
    let package_path = adb.shell(&["pm", "path", ESPEAK_PACKAGE])?;
    if package_path.trim().is_empty() {
        return Err(LauncherError::TtsProvisioningRequired.into());
    }
    Ok(())
}

pub fn installed_version(adb: &Adb, package_name: &str) -> Result<Option<u64>> {
    let result = adb.shell(&["dumpsys", "package", package_name]);
    let output = match result {
        Ok(value) => value,
        Err(_) => return Ok(None),
    };
    let version = output
        .lines()
        .find_map(|line| line.trim().strip_prefix("versionCode="))
        .and_then(|value| value.split_whitespace().next())
        .and_then(|value| value.parse().ok());
    Ok(version)
}

pub fn installed_base_apk(adb: &Adb, package_name: &str) -> Result<Option<String>> {
    let output = match adb.shell(&["pm", "path", package_name]) {
        Ok(value) => value,
        Err(_) => return Ok(None),
    };
    Ok(output
        .lines()
        .find_map(|line| line.strip_prefix("package:"))
        .map(str::to_owned))
}

pub fn verify_guest(adb: &Adb, expected_api: u32, expected_abi: &str) -> Result<()> {
    anyhow::ensure!(
        adb.shell(&["getprop", "ro.build.version.sdk"])? == expected_api.to_string(),
        "guest API does not match selected backend"
    );
    anyhow::ensure!(
        adb.shell(&["getprop", "ro.product.cpu.abi"])? == expected_abi,
        "guest ABI does not match selected backend"
    );
    Ok(())
}
