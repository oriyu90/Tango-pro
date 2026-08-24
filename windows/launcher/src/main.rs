#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod adb;
mod app;
mod emulator;
mod errors;
mod integrity;
mod ipc;
mod logging;
mod paths;
mod preflight;
mod process;
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
    time::Duration,
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
    let app_lock = integrity::verify_payload(&paths)?;

    machine.advance(State::VerifyRuntime, State::AllocatePort)?;
    log.event(machine.current().label());
    let port = preflight::allocate_emulator_port()?;

    machine.advance(State::AllocatePort, State::PrepareUserdata)?;
    log.event(machine.current().label());
    paths.prepare_userdata()?;

    let job = process::Job::new()?;

    machine.advance(State::PrepareUserdata, State::StartEmulator)?;
    log.event(machine.current().label());
    let _emulator = emulator::start(&paths, &job, port)?;
    let adb = adb::Adb::new(&paths, port);

    machine.advance(State::StartEmulator, State::WaitAdb)?;
    log.event(machine.current().label());
    adb.wait_for_device(emulator::ADB_TIMEOUT)?;

    machine.advance(State::WaitAdb, State::WaitBoot)?;
    log.event(machine.current().label());
    adb.wait_for_boot(emulator::BOOT_TIMEOUT)?;

    machine.advance(State::WaitBoot, State::VerifyGuest)?;
    log.event(machine.current().label());
    app::verify_guest(&adb)?;

    machine.advance(State::VerifyGuest, State::VerifyApp)?;
    log.event(machine.current().label());
    // Payload SHA-256 is verified before this point. APK certificate verification is a release-bundle preflight.

    machine.advance(State::VerifyApp, State::UpdateApp)?;
    log.event(machine.current().label());
    updater::update_if_needed(&adb, &paths, &app_lock)?;

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
    loop {
        if shutdown_requested.load(Ordering::Acquire) {
            log.event("shutdown requested by IPC");
            let _ = viewer.kill();
            let _ = viewer.wait();
            break;
        }
        if let Some(viewer_status) = viewer.try_wait()? {
            if !viewer_status.success() {
                anyhow::bail!("viewer terminated unexpectedly: {viewer_status}");
            }
            break;
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
