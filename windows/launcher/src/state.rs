#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum State {
    Start,
    Preflight,
    VerifyRuntime,
    AllocatePort,
    PrepareUserdata,
    StartEmulator,
    WaitAdb,
    WaitBoot,
    VerifyGuest,
    VerifyApp,
    UpdateApp,
    StartTango,
    StartViewer,
    Running,
    Shutdown,
    Exit,
}

impl State {
    pub const fn label(self) -> &'static str {
        match self {
            Self::Start => "START",
            Self::Preflight => "PREFLIGHT",
            Self::VerifyRuntime => "VERIFY_RUNTIME",
            Self::AllocatePort => "ALLOCATE_PORT",
            Self::PrepareUserdata => "PREPARE_USERDATA",
            Self::StartEmulator => "START_EMULATOR",
            Self::WaitAdb => "WAIT_ADB",
            Self::WaitBoot => "WAIT_BOOT",
            Self::VerifyGuest => "VERIFY_GUEST",
            Self::VerifyApp => "VERIFY_APP",
            Self::UpdateApp => "UPDATE_APP",
            Self::StartTango => "START_TANGO",
            Self::StartViewer => "START_VIEWER",
            Self::Running => "RUNNING",
            Self::Shutdown => "SHUTDOWN",
            Self::Exit => "EXIT",
        }
    }
}

#[derive(Debug)]
pub struct Machine {
    current: State,
}

impl Machine {
    pub const fn new() -> Self {
        Self {
            current: State::Start,
        }
    }

    pub const fn current(&self) -> State {
        self.current
    }

    pub fn advance(&mut self, expected: State, next: State) -> anyhow::Result<()> {
        anyhow::ensure!(
            self.current == expected,
            "invalid state transition: expected {}, found {}",
            expected.label(),
            self.current.label()
        );
        self.current = next;
        Ok(())
    }
}
