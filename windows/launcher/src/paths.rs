use std::{env, fs, path::PathBuf};

use anyhow::{Context, Result};

#[derive(Clone, Debug)]
pub struct Paths {
    pub install: PathBuf,
    pub userdata: PathBuf,
    pub android_sdk_root: PathBuf,
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
        let android_sdk_root = Self::dedicated_android_sdk_root(&userdata);
        Ok(Self {
            install,
            userdata,
            android_sdk_root,
        })
    }

    pub fn dedicated_android_sdk_root(userdata: &std::path::Path) -> PathBuf {
        env::var_os("TANGO_DEV_ROOT")
            .map(PathBuf::from)
            .map(|root| root.join("android-sdk"))
            .unwrap_or_else(|| userdata.join("runtime").join("android-sdk"))
    }

    pub fn with_android_sdk(&self, root: PathBuf) -> Self {
        Self {
            install: self.install.clone(),
            userdata: self.userdata.clone(),
            android_sdk_root: root,
        }
    }

    pub fn runtime_lock(&self) -> PathBuf {
        self.install.join("runtime.lock.json")
    }
    pub fn components_lock(&self) -> PathBuf {
        self.install.join("components.lock.json")
    }
    pub fn sdk_bootstrap_lock(&self) -> PathBuf {
        self.install.join("sdk-bootstrap.lock.json")
    }
    pub fn sdk_setup_script(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("setup-android-runtime.ps1")
    }
    pub fn app_lock(&self) -> PathBuf {
        self.install.join("app.lock.json")
    }
    pub fn payload_apk(&self) -> PathBuf {
        self.install.join("payload").join("TangoPro.apk")
    }
    pub fn adb(&self) -> PathBuf {
        self.android_sdk_root.join("platform-tools").join("adb.exe")
    }
    pub fn apksigner(&self, build_tools_version: &str) -> PathBuf {
        self.android_sdk_root
            .join("build-tools")
            .join(build_tools_version)
            .join("apksigner.bat")
    }
    pub fn emulator(&self) -> PathBuf {
        self.android_sdk_root.join("emulator").join("emulator.exe")
    }
    pub fn qemu(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("qemu")
            .join("qemu-system-x86_64.exe")
    }
    pub fn guest_manifest(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("guest")
            .join("manifest.json")
    }
    pub fn guest_kernel(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("guest")
            .join("AndroidOS")
            .join("kernel")
    }
    pub fn guest_initrd(&self) -> PathBuf {
        self.install
            .join("runtime")
            .join("guest")
            .join("AndroidOS")
            .join("initrd.img")
    }
    pub fn guest_system(&self, artifact: &str) -> PathBuf {
        self.install
            .join("runtime")
            .join("guest")
            .join("AndroidOS")
            .join(artifact)
    }
    pub fn guest_data_image(&self) -> PathBuf {
        self.guest_data().join("data.img")
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
            .unwrap_or_else(|| self.userdata.join("runtime").join("avd"))
    }
    pub fn avd_name(&self) -> String {
        env::var("TANGO_AVD_NAME").unwrap_or_else(|_| "TangoPro_API35_x86_64".to_owned())
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
            &self.userdata.join("runtime"),
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
