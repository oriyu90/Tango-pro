use std::{process::Child, time::Duration};

use anyhow::Result;

use crate::{paths::Paths, process::Job};

pub struct GuestHandle {
    pub(crate) child: Child,
    pub adb_serial: String,
}

impl GuestHandle {
    pub fn try_wait(&mut self) -> Result<Option<std::process::ExitStatus>> {
        Ok(self.child.try_wait()?)
    }
}

pub trait GuestBackend {
    fn name(&self) -> &'static str;
    fn android_api(&self) -> u32;
    fn architecture(&self) -> &'static str;
    fn preflight(&self, paths: &Paths) -> Result<()>;
    fn prepare(&self, paths: &Paths) -> Result<()>;
    fn start(&self, paths: &Paths, job: &Job, port: u16) -> Result<GuestHandle>;
    fn adb_timeout(&self) -> Duration;
    fn boot_timeout(&self) -> Duration;
}
