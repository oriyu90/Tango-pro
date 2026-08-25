use std::{
    fs::File,
    process::{Child, Command, Stdio},
    time::Duration,
};

use anyhow::{Context, Result};

use crate::{paths::Paths, process::Job};

pub fn start(paths: &Paths, job: &Job, port: u16) -> Result<Child> {
    let mut command = Command::new(paths.emulator());
    command.args([
        "-avd",
        &paths.avd_name(),
        "-no-window",
        "-no-boot-anim",
        "-gpu",
        "auto",
        "-no-snapshot",
        "-port",
        &port.to_string(),
    ]);
    command.env("ANDROID_AVD_HOME", paths.avd_home());
    command.env("ANDROID_HOME", &paths.android_sdk_root);
    command.env("ANDROID_SDK_ROOT", &paths.android_sdk_root);
    let stdout = File::create(paths.logs().join("emulator.stdout.log"))?;
    let stderr = File::create(paths.logs().join("emulator.stderr.log"))?;
    command
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));
    let child = command
        .spawn()
        .with_context(|| format!("cannot start {}", paths.emulator().display()))?;
    job.assign_child(&child)?;
    Ok(child)
}

pub const ADB_TIMEOUT: Duration = Duration::from_secs(90);
pub const BOOT_TIMEOUT: Duration = Duration::from_secs(180);
