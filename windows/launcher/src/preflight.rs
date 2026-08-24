use std::{env, net::TcpListener, path::Path};

use anyhow::{Context, Result};

use crate::paths::Paths;

pub fn check(paths: &Paths) -> Result<()> {
    anyhow::ensure!(
        env::consts::ARCH == "x86_64",
        "Tango Pro requires 64-bit Windows on x86_64"
    );
    anyhow::ensure!(
        paths.install.is_dir(),
        "runtime install directory is unavailable"
    );
    ensure_writable(&paths.userdata)?;
    Ok(())
}

pub fn allocate_emulator_port() -> Result<u16> {
    for port in (5554_u16..=5680).step_by(2) {
        if TcpListener::bind(("127.0.0.1", port)).is_ok()
            && TcpListener::bind(("127.0.0.1", port + 1)).is_ok()
        {
            return Ok(port);
        }
    }
    anyhow::bail!("no paired emulator port is available")
}

fn ensure_writable(path: &Path) -> Result<()> {
    std::fs::create_dir_all(path).with_context(|| format!("cannot create {}", path.display()))?;
    let probe = path.join(".write-probe");
    std::fs::write(&probe, b"ok").with_context(|| format!("cannot write {}", path.display()))?;
    std::fs::remove_file(&probe)?;
    Ok(())
}
