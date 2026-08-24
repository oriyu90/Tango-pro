use std::{io, thread, time::Duration};

use windows_sys::Win32::{
    Foundation::{
        CloseHandle, ERROR_PIPE_CONNECTED, GENERIC_READ, GENERIC_WRITE, HANDLE,
        INVALID_HANDLE_VALUE,
    },
    Storage::FileSystem::{
        CreateFileW, FILE_ATTRIBUTE_NORMAL, FILE_SHARE_READ, FILE_SHARE_WRITE, OPEN_EXISTING,
        PIPE_ACCESS_DUPLEX, ReadFile, WriteFile,
    },
    System::Pipes::{
        ConnectNamedPipe, CreateNamedPipeW, DisconnectNamedPipe, PIPE_READMODE_MESSAGE,
        PIPE_TYPE_MESSAGE, PIPE_UNLIMITED_INSTANCES, PIPE_WAIT, WaitNamedPipeW,
    },
};

pub const PIPE_NAME: &str = r"\\.\pipe\TangoPro.Runtime.v1";

#[derive(Clone, Debug)]
pub enum Request {
    Focus,
    OpenFile(String),
    Shutdown,
    GetStatus,
}

impl Request {
    fn encode(&self) -> String {
        match self {
            Self::Focus => "FOCUS".to_owned(),
            Self::OpenFile(path) => format!("OPEN_FILE\n{path}"),
            Self::Shutdown => "SHUTDOWN".to_owned(),
            Self::GetStatus => "GET_STATUS".to_owned(),
        }
    }

    fn decode(value: &str) -> Option<Self> {
        match value.trim() {
            "FOCUS" => Some(Self::Focus),
            "SHUTDOWN" => Some(Self::Shutdown),
            "GET_STATUS" => Some(Self::GetStatus),
            _ => value
                .strip_prefix("OPEN_FILE\n")
                .filter(|path| !path.is_empty())
                .map(|path| Self::OpenFile(path.to_owned())),
        }
    }
}

pub fn forward(request: Request) -> io::Result<String> {
    let pipe = wide(PIPE_NAME);
    if unsafe { WaitNamedPipeW(pipe.as_ptr(), 750) } == 0 {
        return Err(io::Error::last_os_error());
    }
    let handle = unsafe {
        CreateFileW(
            pipe.as_ptr(),
            GENERIC_READ | GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            std::ptr::null(),
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL,
            std::ptr::null_mut(),
        )
    };
    if handle == INVALID_HANDLE_VALUE {
        return Err(io::Error::last_os_error());
    }
    let result = transact(handle, &request.encode());
    unsafe {
        CloseHandle(handle);
    }
    result
}

pub fn serve(handler: impl Fn(Request) -> String + Send + Sync + 'static) {
    let handler = std::sync::Arc::new(handler);
    thread::spawn(move || {
        loop {
            let pipe = wide(PIPE_NAME);
            let handle = unsafe {
                CreateNamedPipeW(
                    pipe.as_ptr(),
                    PIPE_ACCESS_DUPLEX,
                    PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE | PIPE_WAIT,
                    PIPE_UNLIMITED_INSTANCES,
                    4096,
                    4096,
                    0,
                    std::ptr::null(),
                )
            };
            if handle == INVALID_HANDLE_VALUE {
                return;
            }
            let connected = unsafe { ConnectNamedPipe(handle, std::ptr::null_mut()) } != 0
                || io::Error::last_os_error().raw_os_error() == Some(ERROR_PIPE_CONNECTED as i32);
            if connected {
                let mut input = [0_u8; 4096];
                let mut read = 0_u32;
                if unsafe {
                    ReadFile(
                        handle,
                        input.as_mut_ptr(),
                        input.len() as u32,
                        &mut read,
                        std::ptr::null_mut(),
                    )
                } != 0
                {
                    let request = String::from_utf8_lossy(&input[..read as usize]);
                    let response = Request::decode(&request)
                        .map(|value| handler(value))
                        .unwrap_or_else(|| "ERROR invalid request".to_owned());
                    let mut written = 0_u32;
                    let _ = unsafe {
                        WriteFile(
                            handle,
                            response.as_ptr(),
                            response.len() as u32,
                            &mut written,
                            std::ptr::null_mut(),
                        )
                    };
                }
            }
            unsafe {
                DisconnectNamedPipe(handle);
                CloseHandle(handle);
            }
            thread::sleep(Duration::from_millis(10));
        }
    });
}

fn transact(handle: HANDLE, request: &str) -> io::Result<String> {
    let mut written = 0_u32;
    if unsafe {
        WriteFile(
            handle,
            request.as_ptr(),
            request.len() as u32,
            &mut written,
            std::ptr::null_mut(),
        )
    } == 0
    {
        return Err(io::Error::last_os_error());
    }
    let mut output = [0_u8; 4096];
    let mut read = 0_u32;
    if unsafe {
        ReadFile(
            handle,
            output.as_mut_ptr(),
            output.len() as u32,
            &mut read,
            std::ptr::null_mut(),
        )
    } == 0
    {
        return Err(io::Error::last_os_error());
    }
    Ok(String::from_utf8_lossy(&output[..read as usize]).into_owned())
}

fn wide(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}
