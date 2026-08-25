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
    ensure_x86_64_v2()?;
    Ok(())
}

fn ensure_x86_64_v2() -> Result<()> {
    #[cfg(target_arch = "x86_64")]
    {
        use std::arch::x86_64::__cpuid;
        let basic = __cpuid(1);
        let extended_max = __cpuid(0x8000_0000).eax;
        let extended = if extended_max >= 0x8000_0001 {
            __cpuid(0x8000_0001)
        } else {
            return Err(anyhow::anyhow!("HOST_CPU_TOO_OLD"));
        };
        let required = (basic.ecx & (1 << 0) != 0)
            && (basic.ecx & (1 << 9) != 0)
            && (basic.ecx & (1 << 13) != 0)
            && (basic.ecx & (1 << 19) != 0)
            && (basic.ecx & (1 << 20) != 0)
            && (basic.ecx & (1 << 23) != 0)
            && (extended.ecx & 1 != 0);
        anyhow::ensure!(required, "HOST_CPU_TOO_OLD");
        return Ok(());
    }
    #[allow(unreachable_code)]
    Err(anyhow::anyhow!("HOST_CPU_TOO_OLD"))
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
