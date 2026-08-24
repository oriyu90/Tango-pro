use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::Path,
};

use anyhow::Result;

pub struct Logger {
    file: std::fs::File,
}

impl Logger {
    pub fn open(log_dir: &Path) -> Result<Self> {
        fs::create_dir_all(log_dir)?;
        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(log_dir.join("launcher.log"))?;
        Ok(Self { file })
    }

    pub fn event(&mut self, message: impl AsRef<str>) {
        let line = format!("{} {}\n", chrono_stamp(), message.as_ref());
        let _ = self.file.write_all(line.as_bytes());
        let _ = self.file.flush();
    }
}

fn chrono_stamp() -> String {
    // A monotonic ordering is not required in the log; avoid a time-zone dependency.
    format!("{:?}", std::time::SystemTime::now())
}
