use std::path::Path;

use anyhow::{Context, Result};
use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct GuestManifest {
    pub schema: u32,
    pub id: String,
    #[serde(rename = "type")]
    pub guest_type: String,
    pub upstream: Upstream,
    pub runtime: Runtime,
    pub network: Network,
    pub status: String,
}

#[derive(Debug, Deserialize)]
pub struct Upstream {
    #[serde(rename = "androidVersion")]
    pub android_version: String,
    pub architecture: String,
    pub variant: String,
}

#[derive(Debug, Deserialize)]
pub struct Runtime {
    pub engine: String,
    #[serde(rename = "ramMb")]
    pub ram_mb: u32,
    #[serde(rename = "cpuCount")]
    pub cpu_count: u32,
    #[serde(rename = "systemArtifact")]
    pub system_artifact: String,
}

#[derive(Debug, Deserialize)]
pub struct Network {
    pub internet: bool,
    pub adb: bool,
}

impl GuestManifest {
    pub fn read(path: &Path) -> Result<Self> {
        let bytes = std::fs::read(path)
            .with_context(|| format!("cannot read guest manifest {}", path.display()))?;
        let manifest: Self = serde_json::from_slice(&bytes)
            .with_context(|| format!("invalid guest manifest {}", path.display()))?;
        anyhow::ensure!(
            manifest.schema == 1,
            "GUEST_MANIFEST_INVALID: unsupported schema"
        );
        anyhow::ensure!(
            !manifest.id.is_empty(),
            "GUEST_MANIFEST_INVALID: missing ID"
        );
        anyhow::ensure!(
            manifest.guest_type == "prebuilt-qemu",
            "GUEST_MANIFEST_INVALID: unexpected backend"
        );
        anyhow::ensure!(
            manifest.upstream.architecture == "x86_64",
            "GUEST_ARCH_UNSUPPORTED"
        );
        anyhow::ensure!(
            manifest.upstream.variant == "FOSS",
            "GUEST_MANIFEST_INVALID: only FOSS guests are allowed"
        );
        anyhow::ensure!(
            manifest.runtime.engine == "qemu",
            "GUEST_MANIFEST_INVALID: engine mismatch"
        );
        anyhow::ensure!(
            matches!(
                manifest.runtime.system_artifact.as_str(),
                "system.sfs" | "system.efs"
            ),
            "GUEST_MANIFEST_INVALID: unsupported system artifact"
        );
        anyhow::ensure!(
            !manifest.network.internet && manifest.network.adb,
            "GUEST_MANIFEST_INVALID: guest network policy mismatch"
        );
        anyhow::ensure!(
            manifest.status == "approved",
            "GUEST_UNSUPPORTED: guest is not approved"
        );
        Ok(manifest)
    }

    pub fn android_api(&self) -> Result<u32> {
        self.upstream
            .android_version
            .parse()
            .context("GUEST_MANIFEST_INVALID: Android version is not numeric")
    }
}
