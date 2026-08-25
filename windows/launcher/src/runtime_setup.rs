use std::{
    collections::HashSet,
    env, fs,
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::{errors::LauncherError, paths::Paths};

#[derive(Clone, Debug, Deserialize)]
pub struct SdkBootstrapLock {
    pub schema: u32,
    #[serde(rename = "runtimeVersion")]
    pub runtime_version: String,
    #[serde(rename = "commandLineTools")]
    pub command_line_tools: CommandLineTools,
    pub packages: SdkPackages,
    pub avd: AvdSpec,
}

#[derive(Clone, Debug, Deserialize)]
pub struct CommandLineTools {
    pub version: String,
    #[serde(rename = "sourceUrl")]
    pub source_url: String,
    pub sha256: String,
    #[serde(rename = "downloadedDirectlyByUser")]
    pub downloaded_directly_by_user: bool,
}

#[derive(Clone, Debug, Deserialize)]
pub struct SdkPackages {
    pub emulator: PackageSpec,
    #[serde(rename = "platformTools")]
    pub platform_tools: PackageSpec,
    #[serde(rename = "buildTools")]
    pub build_tools: PackageSpec,
    #[serde(rename = "systemImage")]
    pub system_image: SystemImageSpec,
}

#[derive(Clone, Debug, Deserialize)]
pub struct PackageSpec {
    pub id: String,
    pub revision: String,
}

#[derive(Clone, Debug, Deserialize)]
pub struct SystemImageSpec {
    pub id: String,
    pub revision: String,
    #[serde(rename = "androidApi")]
    pub android_api: u32,
    pub abi: String,
    pub tag: String,
}

#[derive(Clone, Debug, Deserialize)]
pub struct AvdSpec {
    pub name: String,
    #[serde(rename = "deviceProfile")]
    pub device_profile: String,
    #[serde(rename = "ramMb")]
    pub ram_mb: u32,
    #[serde(rename = "cpuCount")]
    pub cpu_count: u32,
    pub gpu: String,
    #[serde(rename = "snapshotsEnabled")]
    pub snapshots_enabled: bool,
}

#[derive(Clone, Debug)]
pub struct ResolvedSdk {
    pub root: PathBuf,
    pub source: SdkSource,
    pub lock: SdkBootstrapLock,
}

#[derive(Clone, Copy, Debug)]
pub enum SdkSource {
    TangoDedicated,
    ExistingSdk,
}

impl SdkSource {
    pub const fn label(self) -> &'static str {
        match self {
            Self::TangoDedicated => "Tango dedicated SDK",
            Self::ExistingSdk => "existing SDK (read-only)",
        }
    }
}

#[derive(Debug, Serialize)]
struct RuntimeState<'a> {
    schema: u32,
    state: &'a str,
    #[serde(rename = "sdkRoot")]
    sdk_root: Option<String>,
    detail: Option<String>,
}

pub fn ensure_ready(paths: &Paths) -> Result<ResolvedSdk> {
    let lock = load_lock(paths)?;
    let resolved = find_valid_sdk(paths, &lock)?;
    if let Some(resolved) = resolved {
        if avd_is_valid(paths, &lock) {
            write_state(paths, "BOOTSTRAPPING_GUEST", Some(&resolved.root), None)?;
            return Ok(resolved);
        }
        if matches!(resolved.source, SdkSource::ExistingSdk)
            && !has_accepted_licenses(&resolved.root)
        {
            write_state(
                paths,
                "LICENSE_REQUIRED",
                Some(&resolved.root),
                Some("Existing SDK has no accepted SDK license record."),
            )?;
            return Err(LauncherError::RuntimeLicenseRequired.into());
        }
        launch_setup(paths, Some(&resolved.root))?;
    } else {
        write_state(paths, "NOT_INSTALLED", None, None)?;
        launch_setup(paths, None)?;
    }

    let resolved = find_valid_sdk(paths, &lock)?.ok_or(LauncherError::RuntimeSetupIncomplete)?;
    if !avd_is_valid(paths, &lock) {
        write_state(
            paths,
            "BROKEN",
            Some(&resolved.root),
            Some("Setup completed without the locked Tango AVD."),
        )?;
        return Err(LauncherError::RuntimeSetupIncomplete.into());
    }
    write_state(paths, "BOOTSTRAPPING_GUEST", Some(&resolved.root), None)?;
    Ok(resolved)
}

pub fn mark_updating(paths: &Paths) -> Result<()> {
    write_state(paths, "UPDATING", Some(&paths.android_sdk_root), None)
}

pub fn mark_ready(paths: &Paths) -> Result<()> {
    write_state(paths, "READY", Some(&paths.android_sdk_root), None)
}

fn load_lock(paths: &Paths) -> Result<SdkBootstrapLock> {
    let bytes = fs::read(paths.sdk_bootstrap_lock())
        .with_context(|| format!("cannot read {}", paths.sdk_bootstrap_lock().display()))?;
    let lock: SdkBootstrapLock = serde_json::from_slice(&bytes)
        .with_context(|| format!("invalid JSON: {}", paths.sdk_bootstrap_lock().display()))?;
    anyhow::ensure!(lock.schema == 1, "unsupported SDK bootstrap lock schema");
    anyhow::ensure!(
        !lock.runtime_version.is_empty() && !lock.command_line_tools.version.is_empty(),
        "SDK bootstrap runtime/version is missing"
    );
    anyhow::ensure!(
        lock.command_line_tools.downloaded_directly_by_user,
        "SDK bootstrap must be user-acquired"
    );
    anyhow::ensure!(
        lock.command_line_tools
            .source_url
            .starts_with("https://dl.google.com/"),
        "SDK bootstrap source must be the official dl.google.com HTTPS endpoint"
    );
    anyhow::ensure!(
        lock.command_line_tools.sha256.len() == 64
            && lock
                .command_line_tools
                .sha256
                .chars()
                .all(|value| value.is_ascii_hexdigit()),
        "SDK bootstrap SHA-256 is invalid"
    );
    anyhow::ensure!(
        lock.system_image().android_api == 35 && lock.system_image().abi == "x86_64",
        "unsupported SDK guest lock"
    );
    anyhow::ensure!(
        lock.system_image()
            .id
            .split(';')
            .any(|part| part == lock.system_image().tag),
        "SDK system-image tag does not match its package id"
    );
    anyhow::ensure!(
        !lock.avd.name.is_empty()
            && !lock.avd.device_profile.is_empty()
            && !lock.avd.snapshots_enabled,
        "SDK AVD lock is invalid"
    );
    Ok(lock)
}

impl SdkBootstrapLock {
    fn system_image(&self) -> &SystemImageSpec {
        &self.packages.system_image
    }
}

fn find_valid_sdk(paths: &Paths, lock: &SdkBootstrapLock) -> Result<Option<ResolvedSdk>> {
    let dedicated = Paths::dedicated_android_sdk_root(&paths.userdata);
    for root in sdk_candidates(&dedicated) {
        if verify_sdk(&root, lock).is_ok() {
            let source = if root == dedicated {
                SdkSource::TangoDedicated
            } else {
                SdkSource::ExistingSdk
            };
            return Ok(Some(ResolvedSdk {
                root,
                source,
                lock: lock.clone(),
            }));
        }
    }
    Ok(None)
}

fn sdk_candidates(dedicated: &Path) -> Vec<PathBuf> {
    let mut roots = vec![dedicated.to_path_buf()];
    for key in ["ANDROID_SDK_ROOT", "ANDROID_HOME"] {
        if let Some(value) = env::var_os(key) {
            roots.push(PathBuf::from(value));
        }
    }
    if let Some(local) = env::var_os("LOCALAPPDATA") {
        roots.push(PathBuf::from(local).join("Android").join("Sdk"));
    }
    if let Some(profile) = env::var_os("USERPROFILE") {
        roots.push(
            PathBuf::from(profile)
                .join("AppData")
                .join("Local")
                .join("Android")
                .join("Sdk"),
        );
    }
    let mut seen = HashSet::new();
    roots
        .into_iter()
        .filter(|root| seen.insert(root.clone()))
        .collect()
}

fn verify_sdk(root: &Path, lock: &SdkBootstrapLock) -> Result<()> {
    let packages = &lock.packages;
    require_package_revision(
        root.join(package_directory(&packages.emulator.id)?),
        &packages.emulator.id,
        &packages.emulator.revision,
    )?;
    require_package_revision(
        root.join(package_directory(&packages.platform_tools.id)?),
        &packages.platform_tools.id,
        &packages.platform_tools.revision,
    )?;
    require_package_revision(
        root.join(package_directory(&packages.build_tools.id)?),
        &packages.build_tools.id,
        &packages.build_tools.revision,
    )?;
    require_package_revision(
        root.join(package_directory(&packages.system_image.id)?),
        &packages.system_image.id,
        &packages.system_image.revision,
    )?;
    for required in [
        root.join("emulator").join("emulator.exe"),
        root.join("platform-tools").join("adb.exe"),
        root.join(package_directory(&packages.build_tools.id)?)
            .join("apksigner.bat"),
        root.join("cmdline-tools")
            .join("latest")
            .join("bin")
            .join("avdmanager.bat"),
    ] {
        anyhow::ensure!(
            required.is_file(),
            "RUNTIME_PACKAGE_UNAVAILABLE: {}",
            required.display()
        );
    }
    Ok(())
}

fn package_directory(id: &str) -> Result<PathBuf> {
    let mut path = PathBuf::new();
    for part in id.split(';') {
        anyhow::ensure!(
            !part.is_empty() && part != "." && part != ".." && !part.contains(['/', '\\']),
            "invalid SDK package id: {id}"
        );
        path.push(part);
    }
    Ok(path)
}

fn require_package_revision(
    directory: PathBuf,
    expected_id: &str,
    expected_revision: &str,
) -> Result<()> {
    let properties = directory.join("source.properties");
    let body = fs::read_to_string(&properties)
        .with_context(|| format!("RUNTIME_PACKAGE_UNAVAILABLE: {}", properties.display()))?;
    let actual = body
        .lines()
        .find_map(|line| line.strip_prefix("Pkg.Revision="))
        .context("RUNTIME_PACKAGE_UNAVAILABLE: Pkg.Revision is missing")?;
    anyhow::ensure!(
        actual.trim() == expected_revision,
        "RUNTIME_PACKAGE_REVISION_MISMATCH: source.properties expected {expected_revision}, found {actual}"
    );
    if let Some(path) = body.lines().find_map(|line| line.strip_prefix("Pkg.Path=")) {
        anyhow::ensure!(
            path.trim() == expected_id,
            "RUNTIME_PACKAGE_REVISION_MISMATCH: source.properties package id expected {expected_id}, found {path}"
        );
    }
    let package_xml = directory.join("package.xml");
    let xml = fs::read_to_string(&package_xml)
        .with_context(|| format!("RUNTIME_PACKAGE_UNAVAILABLE: {}", package_xml.display()))?;
    let package_id = xml_attribute(&xml, "localPackage", "path")
        .context("RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml localPackage path is missing")?;
    anyhow::ensure!(
        package_id == expected_id,
        "RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml expected {expected_id}, found {package_id}"
    );
    let revision_xml = xml_tag_body(&xml, "revision")
        .context("RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml revision is missing")?;
    let xml_revision = [
        xml_tag_body(revision_xml, "major").context("package.xml revision major is missing")?,
        xml_tag_body(revision_xml, "minor").unwrap_or("0"),
        xml_tag_body(revision_xml, "micro").unwrap_or("0"),
    ];
    let xml_revision = if xml_revision[1] == "0" && xml_revision[2] == "0" {
        xml_revision[0].to_owned()
    } else if xml_revision[2] == "0" {
        format!("{}.{}", xml_revision[0], xml_revision[1])
    } else {
        format!(
            "{}.{}.{}",
            xml_revision[0], xml_revision[1], xml_revision[2]
        )
    };
    anyhow::ensure!(
        xml_revision == expected_revision,
        "RUNTIME_PACKAGE_REVISION_MISMATCH: package.xml expected {expected_revision}, found {xml_revision}"
    );
    Ok(())
}

fn xml_attribute<'a>(xml: &'a str, tag: &str, attribute: &str) -> Option<&'a str> {
    let start = xml.find(&format!("<{tag}"))?;
    let remainder = &xml[start..xml[start..].find('>')? + start];
    let marker = format!("{attribute}=\"");
    let value = remainder.split_once(&marker)?.1;
    Some(value.split_once('"')?.0)
}

fn xml_tag_body<'a>(xml: &'a str, tag: &str) -> Option<&'a str> {
    let start_marker = format!("<{tag}>");
    let end_marker = format!("</{tag}>");
    let after_start = xml.split_once(&start_marker)?.1;
    Some(after_start.split_once(&end_marker)?.0.trim())
}

fn avd_is_valid(paths: &Paths, lock: &SdkBootstrapLock) -> bool {
    let config = paths
        .avd_home()
        .join(format!("{}.avd", paths.avd_name()))
        .join("config.ini");
    let Ok(body) = fs::read_to_string(config) else {
        return false;
    };
    let image_directory = match package_directory(&lock.packages.system_image.id) {
        Ok(value) => value.to_string_lossy().replace('/', "\\"),
        Err(_) => return false,
    };
    let expected = [
        format!("hw.ramSize={}", lock.avd.ram_mb),
        format!("hw.cpu.ncore={}", lock.avd.cpu_count),
        format!("hw.gpu.mode={}", lock.avd.gpu),
        "fastboot.forceColdBoot=yes".to_owned(),
        "fastboot.forceFastBoot=no".to_owned(),
        "PlayStore.enabled=false".to_owned(),
        format!("image.sysdir.1={image_directory}\\"),
    ];
    expected
        .iter()
        .all(|entry| body.lines().any(|line| line.trim() == entry))
}

fn has_accepted_licenses(root: &Path) -> bool {
    root.join("licenses").join("android-sdk-license").is_file()
}

fn launch_setup(paths: &Paths, existing_sdk: Option<&Path>) -> Result<()> {
    let script = paths.sdk_setup_script();
    if !script.is_file() {
        return Err(LauncherError::MissingRuntime(script).into());
    }
    let mut command = Command::new("powershell.exe");
    command.args([
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        &script.to_string_lossy(),
        "-InstallRoot",
        &paths.install.to_string_lossy(),
        "-UserdataRoot",
        &paths.userdata.to_string_lossy(),
    ]);
    if let Some(root) = existing_sdk {
        command.arg("-ExistingSdkRoot").arg(root);
    }
    let status = command
        .status()
        .context("could not open Android Runtime Setup")?;
    if !status.success() {
        let state = fs::read_to_string(
            paths
                .userdata
                .join("runtime")
                .join("android-runtime-state.json"),
        )
        .unwrap_or_default();
        if state.contains("RUNTIME_BOOTSTRAP_HASH_MISMATCH") {
            return Err(LauncherError::RuntimeBootstrapHashMismatch.into());
        }
        if state.contains("RUNTIME_LICENSE_REQUIRED") {
            return Err(LauncherError::RuntimeLicenseRequired.into());
        }
        if state.contains("INSUFFICIENT_DISK_SPACE") {
            return Err(LauncherError::InsufficientDiskSpace.into());
        }
        return Err(LauncherError::RuntimeSetupCancelled.into());
    }
    Ok(())
}

fn write_state(
    paths: &Paths,
    state: &str,
    sdk_root: Option<&Path>,
    detail: Option<&str>,
) -> Result<()> {
    let directory = paths.userdata.join("runtime");
    fs::create_dir_all(&directory)?;
    let final_path = directory.join("android-runtime-state.json");
    let temporary = directory.join("android-runtime-state.json.part");
    let value = RuntimeState {
        schema: 1,
        state,
        sdk_root: sdk_root.map(|root| root.to_string_lossy().into_owned()),
        detail: detail.map(str::to_owned),
    };
    fs::write(&temporary, serde_json::to_vec_pretty(&value)?)?;
    if final_path.exists() {
        fs::remove_file(&final_path)?;
    }
    fs::rename(temporary, final_path)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{package_directory, xml_attribute, xml_tag_body};

    #[test]
    fn parses_sdk_package_xml_revision() {
        let xml = r#"<repository><localPackage path="emulator"><revision><major>37</major><minor>1</minor><micro>11</micro></revision></localPackage></repository>"#;
        assert_eq!(xml_attribute(xml, "localPackage", "path"), Some("emulator"));
        let revision = xml_tag_body(xml, "revision").expect("revision");
        assert_eq!(xml_tag_body(revision, "major"), Some("37"));
        assert_eq!(xml_tag_body(revision, "minor"), Some("1"));
        assert_eq!(xml_tag_body(revision, "micro"), Some("11"));
    }

    #[test]
    fn package_ids_cannot_escape_sdk_root() {
        assert!(package_directory("system-images;android-35;default;x86_64").is_ok());
        assert!(package_directory("..;system-images").is_err());
        assert!(package_directory("system-images;..\\outside").is_err());
    }
}
