use std::{env, fs, path::PathBuf};

use anyhow::{Context, Result};

#[derive(Clone, Debug)]
pub struct Paths {
    pub install: PathBuf,
    pub userdata: PathBuf,
}

impl Paths {
    pub fn discover() -> Result<Self> {
        let install = match env::var_os("TANGO_RUNTIME_ROOT") {
            Some(value) => PathBuf::from(value),
            None => env::current_exe()?
                .parent()
                .context("launcher has no parent directory")?
                .to_path_buf(),
        };
        let userdata = match env::var_os("TANGO_USERDATA_ROOT") {
            Some(value) => PathBuf::from(value),
            None => {
                PathBuf::from(env::var_os("LOCALAPPDATA").context("LOCALAPPDATA is unavailable")?)
                    .join("TangoPro")
            }
        };
        Ok(Self { install, userdata })
    }

    pub fn runtime_lock(&self) -> PathBuf {
        self.install.join("runtime.lock.json")
    }
    pub fn components_lock(&self) -> PathBuf {
        self.install.join("components.lock.json")
    }
    pub fn app_lock(&self) -> PathBuf {
        self.install.join("app.lock.json")
    }
    pub fn payload_apk(&self) -> PathBuf {
        self.install.join("payload").join("TangoPro.apk")
    }
    pub fn adb(&self) -> PathBuf {
        self.install.join("runtime").join("adb").join("adb.exe")
    }
    pub fn apksigner(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("android")
            .join("apksigner.bat")
    }
    pub fn emulator(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("emulator")
            .join("emulator.exe")
    }
    pub fn viewer(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("viewer")
            .join("TangoView.exe")
    }
    pub fn avd_home(&self) -> PathBuf {
        env::var_os("TANGO_AVD_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|| self.userdata.join("avd"))
    }
    pub fn avd_name(&self) -> String {
        env::var("TANGO_AVD_NAME").unwrap_or_else(|_| "TangoPro".to_owned())
    }
    pub fn logs(&self) -> PathBuf {
        self.userdata.join("logs")
    }
    pub fn state(&self) -> PathBuf {
        self.userdata.join("state")
    }
    pub fn guest_data(&self) -> PathBuf {
        self.userdata.join("data")
    }
    pub fn bridge_inbox(&self) -> PathBuf {
        self.userdata.join("bridge").join("inbox")
    }
    pub fn bridge_outbox(&self) -> PathBuf {
        self.userdata.join("bridge").join("outbox")
    }

    pub fn prepare_userdata(&self) -> Result<()> {
        for path in [
            &self.avd_home(),
            &self.logs(),
            &self.state(),
            &self.guest_data(),
            &self.bridge_inbox(),
            &self.bridge_outbox(),
        ] {
            fs::create_dir_all(path)
                .with_context(|| format!("cannot create {}", path.display()))?;
        }
        Ok(())
    }
}
