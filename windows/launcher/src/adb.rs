use std::{
    path::Path,
    process::{Command, Output},
    thread,
    time::{Duration, Instant},
};

use anyhow::{Context, Result};

use crate::paths::Paths;

#[derive(Clone)]
pub struct Adb {
    executable: std::path::PathBuf,
    serial: String,
}

impl Adb {
    pub fn new(paths: &Paths, serial: impl Into<String>) -> Self {
        Self {
            executable: paths.adb(),
            serial: serial.into(),
        }
    }

    pub fn serial(&self) -> &str {
        &self.serial
    }

    pub fn command(&self, args: &[&str]) -> Result<Output> {
        Command::new(&self.executable)
            .arg("-s")
            .arg(&self.serial)
            .args(args)
            .output()
            .with_context(|| format!("cannot run private adb at {}", self.executable.display()))
    }

    pub fn require_success(&self, args: &[&str]) -> Result<String> {
        let output = self.command(args)?;
        anyhow::ensure!(
            output.status.success(),
            "adb {} failed: {}",
            args.join(" "),
            String::from_utf8_lossy(&output.stderr).trim()
        );
        Ok(String::from_utf8_lossy(&output.stdout).trim().to_owned())
    }

    pub fn wait_for_device(&self, timeout: Duration) -> Result<()> {
        let end = Instant::now() + timeout;
        while Instant::now() < end {
            if self.require_success(&["wait-for-device"]).is_ok() {
                return Ok(());
            }
            thread::sleep(Duration::from_secs(1));
        }
        anyhow::bail!("timed out waiting for {}", self.serial)
    }

    pub fn wait_for_boot(&self, timeout: Duration) -> Result<()> {
        let end = Instant::now() + timeout;
        while Instant::now() < end {
            if self
                .require_success(&["shell", "getprop", "sys.boot_completed"])
                .is_ok_and(|value| value == "1")
            {
                return Ok(());
            }
            thread::sleep(Duration::from_secs(1));
        }
        anyhow::bail!("timed out waiting for Android boot")
    }

    pub fn shell(&self, command: &[&str]) -> Result<String> {
        let mut args = vec!["shell"];
        args.extend_from_slice(command);
        self.require_success(&args)
    }

    pub fn install_replace(&self, apk: &Path) -> Result<()> {
        self.require_success(&["install", "-r", &apk.to_string_lossy()])?;
        Ok(())
    }

    pub fn pull(&self, remote: &str, local: &Path) -> Result<()> {
        self.require_success(&["pull", remote, &local.to_string_lossy()])?;
        Ok(())
    }
}
