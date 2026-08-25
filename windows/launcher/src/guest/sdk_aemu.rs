use std::time::Duration;

use anyhow::Result;

use crate::{emulator, paths::Paths, process::Job};

use super::backend::{GuestBackend, GuestHandle};

pub struct SdkAemuGuest;

impl GuestBackend for SdkAemuGuest {
    fn name(&self) -> &'static str {
        "sdk-aemu"
    }
    fn android_api(&self) -> u32 {
        35
    }
    fn architecture(&self) -> &'static str {
        "x86_64"
    }
    fn preflight(&self, _paths: &Paths) -> Result<()> {
        Ok(())
    }
    fn prepare(&self, _paths: &Paths) -> Result<()> {
        Ok(())
    }
    fn start(&self, paths: &Paths, job: &Job, port: u16) -> Result<GuestHandle> {
        Ok(GuestHandle {
            child: emulator::start(paths, job, port)?,
            adb_serial: format!("emulator-{port}"),
        })
    }
    fn adb_timeout(&self) -> Duration {
        emulator::ADB_TIMEOUT
    }
    fn boot_timeout(&self) -> Duration {
        emulator::BOOT_TIMEOUT
    }
}
