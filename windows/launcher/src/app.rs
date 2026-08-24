use anyhow::Result;

use crate::adb::Adb;

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

pub fn verify_guest(adb: &Adb) -> Result<()> {
    anyhow::ensure!(
        adb.shell(&["getprop", "ro.build.version.sdk"])? == "35",
        "guest API must be 35"
    );
    anyhow::ensure!(
        adb.shell(&["getprop", "ro.product.cpu.abi"])? == "x86_64",
        "guest ABI must be x86_64"
    );
    Ok(())
}
