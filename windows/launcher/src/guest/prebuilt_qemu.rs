use std::{
    fs::File,
    process::{Command, Stdio},
    time::Duration,
};

use anyhow::{Context, Result};

use crate::{paths::Paths, process::Job};

use super::{
    backend::{GuestBackend, GuestHandle},
    manifest::GuestManifest,
};

pub struct PrebuiltQemuGuest {
    manifest: GuestManifest,
}

impl PrebuiltQemuGuest {
    pub fn load(paths: &Paths) -> Result<Self> {
        Ok(Self {
            manifest: GuestManifest::read(&paths.guest_manifest())?,
        })
    }
}

impl GuestBackend for PrebuiltQemuGuest {
    fn name(&self) -> &'static str {
        "prebuilt-qemu"
    }
    fn android_api(&self) -> u32 {
        self.manifest.android_api().unwrap_or_default()
    }
    fn architecture(&self) -> &'static str {
        "x86_64"
    }
    fn preflight(&self, paths: &Paths) -> Result<()> {
        anyhow::ensure!(
            paths.qemu().is_file(),
            "QEMU_START_FAILED: runtime QEMU is missing"
        );
        anyhow::ensure!(
            paths.guest_kernel().is_file()
                && paths.guest_initrd().is_file()
                && paths
                    .guest_system(&self.manifest.runtime.system_artifact)
                    .is_file(),
            "GUEST_BOOT_FAILED: guest boot assets are incomplete"
        );
        Ok(())
    }
    fn prepare(&self, paths: &Paths) -> Result<()> {
        anyhow::ensure!(
            paths.guest_data_image().is_file(),
            "GUEST_BOOT_FAILED: persistent factory data image is missing"
        );
        Ok(())
    }
    fn start(&self, paths: &Paths, job: &Job, port: u16) -> Result<GuestHandle> {
        let stdout = File::create(paths.logs().join("qemu.stdout.log"))?;
        let stderr = File::create(paths.logs().join("qemu.stderr.log"))?;
        let mut command = Command::new(paths.qemu());
        command.args([
            "-accel",
            "whpx",
            "-machine",
            "q35",
            "-cpu",
            "max",
            "-m",
            &self.manifest.runtime.ram_mb.to_string(),
            "-smp",
            &self.manifest.runtime.cpu_count.to_string(),
            "-device",
            "virtio-vga",
            "-kernel",
        ]);
        command
            .arg(paths.guest_kernel())
            .arg("-initrd")
            .arg(paths.guest_initrd());
        command.args(["-append", "root=/dev/ram0 quiet nomodeset HWACCEL=0"]);
        command.arg("-drive").arg(format!(
            "file={},format=raw,if=virtio,readonly=on",
            paths
                .guest_system(&self.manifest.runtime.system_artifact)
                .display()
        ));
        command.arg("-drive").arg(format!(
            "file={},format=raw,if=virtio",
            paths.guest_data_image().display()
        ));
        command.args([
            "-netdev",
            &format!("user,id=guestnet,restrict=on,hostfwd=tcp:127.0.0.1:{port}-:5555"),
            "-device",
            "virtio-net-pci,netdev=guestnet",
            "-display",
            "none",
        ]);
        command
            .stdin(Stdio::null())
            .stdout(Stdio::from(stdout))
            .stderr(Stdio::from(stderr));
        let child = command.spawn().with_context(|| {
            format!("QEMU_START_FAILED: cannot start {}", paths.qemu().display())
        })?;
        job.assign_child(&child)?;
        Ok(GuestHandle {
            child,
            adb_serial: format!("127.0.0.1:{port}"),
        })
    }
    fn adb_timeout(&self) -> Duration {
        Duration::from_secs(120)
    }
    fn boot_timeout(&self) -> Duration {
        Duration::from_secs(240)
    }
}
