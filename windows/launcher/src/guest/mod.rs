pub mod backend;
pub mod manifest;
pub mod prebuilt_qemu;
pub mod sdk_aemu;

use anyhow::Result;

use crate::{integrity::RuntimeLock, paths::Paths};

use self::{backend::GuestBackend, prebuilt_qemu::PrebuiltQemuGuest, sdk_aemu::SdkAemuGuest};

pub fn select(paths: &Paths, runtime: &RuntimeLock) -> Result<Box<dyn GuestBackend>> {
    match runtime.guest_backend.as_str() {
        "sdk-aemu" => Ok(Box::new(SdkAemuGuest)),
        "prebuilt-qemu" => Ok(Box::new(PrebuiltQemuGuest::load(paths)?)),
        other => anyhow::bail!("GUEST_UNSUPPORTED: unknown backend {other}"),
    }
}
