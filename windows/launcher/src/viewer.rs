use std::process::{Child, Command, Stdio};

use anyhow::{Context, Result};

use crate::{integrity::AppLock, paths::Paths, process::Job};

pub fn start(paths: &Paths, job: &Job, serial: &str, app: &AppLock) -> Result<Child> {
    let start_app = format!("--start-app={}", app.package_name);
    let serial = format!("--serial={serial}");
    let mut command = Command::new(paths.viewer());
    command.args([
        serial.as_str(),
        "--new-display=900x1440/240",
        "--flex-display",
        "--keep-active",
        "--no-vd-system-decorations",
        start_app.as_str(),
        "--mouse=sdk",
        "--keyboard=uhid",
        "--no-clipboard-autosync",
        "--window-title=Tango Pro",
    ]);
    command
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    let child = command
        .spawn()
        .with_context(|| format!("cannot start {}", paths.viewer().display()))?;
    job.assign_child(&child)?;
    Ok(child)
}
