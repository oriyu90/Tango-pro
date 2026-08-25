use std::{
    fs::File,
    io::{BufReader, Read},
    path::Path,
    process::Command,
};

use anyhow::{Context, Result};
use serde::Deserialize;
use sha2::{Digest, Sha256};

use crate::{errors::LauncherError, paths::Paths, runtime_setup::SdkBootstrapLock};

#[derive(Debug, Deserialize)]
pub struct RuntimeLock {
    pub schema: u32,
    #[serde(rename = "runtimeVersion")]
    pub runtime_version: String,
    #[serde(rename = "guestBackend")]
    pub guest_backend: String,
    pub guest: GuestLock,
}

#[derive(Debug, Deserialize)]
pub struct GuestLock {
    #[serde(rename = "androidApi")]
    pub android_api: u32,
    pub architecture: String,
}

#[derive(Debug, Deserialize)]
pub struct AppLock {
    pub schema: u32,
    #[serde(rename = "packageName")]
    pub package_name: String,
    #[serde(rename = "versionCode")]
    pub version_code: u64,
    pub apk: String,
    pub sha256: String,
    #[serde(rename = "certificateSha256")]
    pub certificate_sha256: String,
}

pub fn verify_runtime(paths: &Paths) -> Result<RuntimeLock> {
    for required in [
        paths.runtime_lock(),
        paths.components_lock(),
        paths.app_lock(),
        paths.viewer(),
        paths.payload_apk(),
    ] {
        if !required.is_file() {
            return Err(LauncherError::MissingRuntime(required).into());
        }
    }
    let lock: RuntimeLock = read_json(&paths.runtime_lock())?;
    anyhow::ensure!(lock.schema == 1, "unsupported runtime lock schema");
    match lock.guest_backend.as_str() {
        "sdk-aemu" => anyhow::ensure!(
            lock.guest.android_api == 35 && lock.guest.architecture == "x86_64",
            "unsupported SDK AEMU guest lock"
        ),
        "prebuilt-qemu" => anyhow::ensure!(
            paths.qemu().is_file() && paths.guest_manifest().is_file(),
            "GUEST_BOOT_FAILED: prebuilt QEMU runtime is incomplete"
        ),
        other => anyhow::bail!("GUEST_UNSUPPORTED: unknown backend {other}"),
    }
    Ok(lock)
}

pub fn verify_payload(paths: &Paths, sdk: &SdkBootstrapLock) -> Result<AppLock> {
    let lock: AppLock = read_json(&paths.app_lock())?;
    anyhow::ensure!(lock.schema == 1, "unsupported app lock schema");
    anyhow::ensure!(
        !lock.package_name.is_empty() && lock.version_code > 0,
        "invalid app lock identity"
    );
    anyhow::ensure!(
        lock.apk.replace('\\', "/") == "payload/TangoPro.apk",
        "app lock must identify the bundled payload"
    );
    let actual = sha256_file(&paths.payload_apk())?;
    if !actual.eq_ignore_ascii_case(&lock.sha256) {
        return Err(LauncherError::Integrity(paths.payload_apk()).into());
    }
    verify_apk_certificate(
        paths,
        &paths.payload_apk(),
        &lock.certificate_sha256,
        &sdk.packages.build_tools.revision,
    )?;
    Ok(lock)
}

pub fn verify_apk_certificate(
    paths: &Paths,
    apk: &Path,
    expected: &str,
    build_tools_version: &str,
) -> Result<()> {
    let apksigner = paths.apksigner(build_tools_version);
    let output = Command::new(&apksigner)
        .args(["verify", "--verbose", "--print-certs"])
        .arg(apk)
        .output()
        .with_context(|| format!("cannot run {}", apksigner.display()))?;
    anyhow::ensure!(
        output.status.success(),
        "apksigner rejected {}: {}",
        apk.display(),
        String::from_utf8_lossy(&output.stderr).trim()
    );
    let combined = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    let actual = combined
        .lines()
        .find_map(|line| {
            line.split_once("certificate SHA-256 digest:")
                .map(|(_, value)| value.trim().to_owned())
        })
        .context("apksigner did not emit a certificate SHA-256 digest")?;
    if normalize_fingerprint(&actual) != normalize_fingerprint(expected) {
        return Err(LauncherError::AppSignatureMismatch.into());
    }
    Ok(())
}

pub fn sha256_file(path: &Path) -> Result<String> {
    let file = File::open(path).with_context(|| format!("cannot open {}", path.display()))?;
    let mut reader = BufReader::new(file);
    let mut hash = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let count = reader.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hash.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hash.finalize()))
}

fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T> {
    let bytes = std::fs::read(path).with_context(|| format!("cannot read {}", path.display()))?;
    serde_json::from_slice(&bytes).with_context(|| format!("invalid JSON: {}", path.display()))
}

fn normalize_fingerprint(value: &str) -> String {
    value
        .chars()
        .filter(|character| character.is_ascii_hexdigit())
        .collect::<String>()
        .to_ascii_lowercase()
}
