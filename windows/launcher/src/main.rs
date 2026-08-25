#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod adb;
mod app;
mod emulator;
mod errors;
mod guest;
mod integrity;
mod ipc;
mod logging;
mod paths;
mod preflight;
mod process;
mod runtime_setup;
mod state;
mod updater;
mod viewer;

use std::{
    env,
    path::PathBuf,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    thread,
    time::{Duration, Instant},
};

use anyhow::{Context, Result};
use ipc::Request;
use paths::Paths;
use state::State;

fn main() {
    if let Err(error) = run() {
        eprintln!("Tango Pro could not start: {error:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let request = parse_request(env::args_os().skip(1))?;
    let instance = match process::InstanceMutex::acquire()? {
        Some(instance) => instance,
        None => {
            ipc::forward(request)
                .context("an existing Tango Pro instance did not accept the request")?;
            return Ok(());
        }
    };
    let paths = Paths::discover()?;
    let mut log = logging::Logger::open(&paths.logs())?;
    let mut machine = state::Machine::new();
    log.event(machine.current().label());

    machine.advance(State::Start, State::Preflight)?;
    log.event(machine.current().label());
    preflight::check(&paths)?;

    machine.advance(State::Preflight, State::VerifyRuntime)?;
    log.event(machine.current().label());
    let runtime = integrity::verify_runtime(&paths)?;
    log.event(format!("runtime {}", runtime.runtime_version));
    let sdk = runtime_setup::ensure_ready(&paths)?;
    log.event(format!("Android SDK source {}", sdk.source.label()));
    let paths = paths.with_android_sdk(sdk.root);
    let build_tools_version = sdk.lock.packages.build_tools.revision.clone();
    let app_lock = integrity::verify_payload(&paths, &sdk.lock)?;
    let guest = guest::select(&paths, &runtime)?;
    guest.preflight(&paths)?;

    machine.advance(State::VerifyRuntime, State::AllocatePort)?;
    log.event(machine.current().label());
    let mut port = preflight::allocate_emulator_port()?;

    machine.advance(State::AllocatePort, State::PrepareUserdata)?;
    log.event(machine.current().label());
    paths.prepare_userdata()?;
    guest.prepare(&paths)?;

    let job = process::Job::new()?;

    machine.advance(State::PrepareUserdata, State::StartEmulator)?;
    log.event(machine.current().label());
    log.event(format!("guest backend {}", guest.name()));
    let mut guest_handle = guest.start(&paths, &job, port)?;
    let mut adb = adb::Adb::new(&paths, guest_handle.adb_serial.clone());

    machine.advance(State::StartEmulator, State::WaitAdb)?;
    log.event(machine.current().label());
    adb.wait_for_device(guest.adb_timeout())?;

    machine.advance(State::WaitAdb, State::WaitBoot)?;
    log.event(machine.current().label());
    adb.wait_for_boot(guest.boot_timeout())?;

    machine.advance(State::WaitBoot, State::VerifyGuest)?;
    log.event(machine.current().label());
    app::verify_guest(&adb, guest.android_api(), guest.architecture())?;

    machine.advance(State::VerifyGuest, State::VerifyApp)?;
    log.event(machine.current().label());
    // Payload SHA-256 is verified before this point. APK certificate verification is a release-bundle preflight.

    machine.advance(State::VerifyApp, State::UpdateApp)?;
    log.event(machine.current().label());
    runtime_setup::mark_updating(&paths)?;
    updater::update_if_needed(&adb, &paths, &app_lock, &build_tools_version)?;
    app::require_offline_tts(&adb)?;
    runtime_setup::mark_ready(&paths)?;

    machine.advance(State::UpdateApp, State::StartTango)?;
    log.event(machine.current().label());
    adb.require_success(&["shell", "monkey", "-p", &app_lock.package_name, "1"])?;

    machine.advance(State::StartTango, State::StartViewer)?;
    log.event(machine.current().label());
    let mut viewer = viewer::start(&paths, &job, adb.serial(), &app_lock)?;

    let status = Arc::new(std::sync::Mutex::new(String::from("RUNNING")));
    let shutdown_requested = Arc::new(AtomicBool::new(false));
    let ipc_status = status.clone();
    let ipc_shutdown = shutdown_requested.clone();
    ipc::serve(move |request| match request {
        Request::Focus => "OK".to_owned(),
        Request::GetStatus => ipc_status
            .lock()
            .map(|value| value.clone())
            .unwrap_or_else(|_| "ERROR".to_owned()),
        Request::OpenFile(path) => match validate_csv_argument(&PathBuf::from(path)) {
            Ok(()) => "ERROR bridge is not yet installed".to_owned(),
            Err(error) => format!("ERROR {error}"),
        },
        Request::Shutdown => {
            ipc_shutdown.store(true, Ordering::Release);
            "OK shutdown requested".to_owned()
        }
    });

    machine.advance(State::StartViewer, State::Running)?;
    log.event(machine.current().label());
    let mut viewer_recovery_attempts = 0_u8;
    let mut tango_recovery_attempts = 0_u8;
    let mut emulator_recovery_attempts = 0_u8;
    let mut next_health_check = Instant::now();
    loop {
        if shutdown_requested.load(Ordering::Acquire) {
            log.event("shutdown requested by IPC");
            let _ = viewer.kill();
            let _ = viewer.wait();
            break;
        }
        if let Some(emulator_status) = guest_handle.try_wait()? {
            if emulator_recovery_attempts >= 1 {
                anyhow::bail!(
                    "emulator terminated after its only recovery attempt: {emulator_status}"
                );
            }
            emulator_recovery_attempts += 1;
            log.event(format!(
                "emulator exited ({emulator_status}); one recovery attempt"
            ));
            let _ = viewer.kill();
            let _ = viewer.wait();
            port = preflight::allocate_emulator_port()?;
            guest_handle = guest.start(&paths, &job, port)?;
            adb = adb::Adb::new(&paths, guest_handle.adb_serial.clone());
            adb.wait_for_device(guest.adb_timeout())?;
            adb.wait_for_boot(guest.boot_timeout())?;
            app::verify_guest(&adb, guest.android_api(), guest.architecture())?;
            runtime_setup::mark_updating(&paths)?;
            updater::update_if_needed(&adb, &paths, &app_lock, &build_tools_version)?;
            app::require_offline_tts(&adb)?;
            runtime_setup::mark_ready(&paths)?;
            adb.require_success(&["shell", "monkey", "-p", &app_lock.package_name, "1"])?;
            viewer = viewer::start(&paths, &job, adb.serial(), &app_lock)?;
            next_health_check = Instant::now() + Duration::from_secs(1);
            continue;
        }
        if let Some(viewer_status) = viewer.try_wait()? {
            if viewer_recovery_attempts >= 1 {
                anyhow::bail!("viewer terminated after its only recovery attempt: {viewer_status}");
            }
            viewer_recovery_attempts += 1;
            log.event(format!(
                "viewer exited ({viewer_status}); one recovery attempt"
            ));
            viewer = viewer::start(&paths, &job, adb.serial(), &app_lock)?;
            continue;
        }
        if Instant::now() >= next_health_check {
            next_health_check = Instant::now() + Duration::from_secs(1);
            if !app::is_running(&adb, &app_lock.package_name) {
                if tango_recovery_attempts >= 1 {
                    anyhow::bail!("Tango process terminated after its only recovery attempt");
                }
                tango_recovery_attempts += 1;
                log.event("Tango process exited; one recovery attempt");
                adb.require_success(&["shell", "monkey", "-p", &app_lock.package_name, "1"])?;
                let _ = viewer.kill();
                let _ = viewer.wait();
                viewer = viewer::start(&paths, &job, adb.serial(), &app_lock)?;
            }
        }
        thread::sleep(Duration::from_millis(200));
    }

    machine.advance(State::Running, State::Shutdown)?;
    log.event(machine.current().label());
    drop(instance);
    machine.advance(State::Shutdown, State::Exit)?;
    log.event(machine.current().label());
    Ok(())
}

fn parse_request(arguments: impl Iterator<Item = std::ffi::OsString>) -> Result<Request> {
    let values: Vec<_> = arguments.collect();
    match values.as_slice() {
        [] => Ok(Request::Focus),
        [value] if value == "--shutdown" => Ok(Request::Shutdown),
        [value] if value == "--status" => Ok(Request::GetStatus),
        [path] => {
            let path = PathBuf::from(path);
            validate_csv_argument(&path)?;
            Ok(Request::OpenFile(path.to_string_lossy().into_owned()))
        }
        _ => anyhow::bail!("only one CSV path, --status, or --shutdown is accepted"),
    }
}

fn validate_csv_argument(path: &std::path::Path) -> Result<()> {
    anyhow::ensure!(path.is_file(), "CSV file does not exist");
    anyhow::ensure!(
        path.extension()
            .is_some_and(|ext| ext.eq_ignore_ascii_case("csv")),
        "only .csv files may be opened"
    );
    let length = std::fs::metadata(path)?.len();
    anyhow::ensure!(
        length <= 64 * 1024 * 1024,
        "CSV file exceeds the 64 MiB transport limit"
    );
    Ok(())
}
