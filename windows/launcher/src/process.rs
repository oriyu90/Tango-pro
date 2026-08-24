use std::{ffi::c_void, io, mem::size_of, os::windows::io::AsRawHandle, process::Child};

use windows_sys::Win32::{
    Foundation::{CloseHandle, ERROR_ALREADY_EXISTS, GetLastError, HANDLE},
    System::{
        JobObjects::{
            AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE,
            JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JobObjectExtendedLimitInformation,
            SetInformationJobObject,
        },
        Threading::CreateMutexW,
    },
};

const INSTANCE_MUTEX: &str = "Local\\TangoPro.Runtime.Singleton";

pub struct InstanceMutex {
    handle: HANDLE,
}

impl InstanceMutex {
    pub fn acquire() -> io::Result<Option<Self>> {
        let name = wide(INSTANCE_MUTEX);
        let handle = unsafe { CreateMutexW(std::ptr::null(), 0, name.as_ptr()) };
        if handle.is_null() {
            return Err(io::Error::last_os_error());
        }
        if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
            unsafe {
                CloseHandle(handle);
            }
            return Ok(None);
        }
        Ok(Some(Self { handle }))
    }
}

impl Drop for InstanceMutex {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.handle);
        }
    }
}

pub struct Job {
    handle: HANDLE,
}

impl Job {
    pub fn new() -> io::Result<Self> {
        let handle = unsafe { CreateJobObjectW(std::ptr::null(), std::ptr::null()) };
        if handle.is_null() {
            return Err(io::Error::last_os_error());
        }
        let mut info = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
        info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        let ok = unsafe {
            SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                &raw const info as *const c_void,
                size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
            )
        };
        if ok == 0 {
            unsafe {
                CloseHandle(handle);
            }
            return Err(io::Error::last_os_error());
        }
        Ok(Self { handle })
    }

    pub fn assign_child(&self, child: &Child) -> io::Result<()> {
        let process = child.as_raw_handle() as HANDLE;
        if unsafe { AssignProcessToJobObject(self.handle, process) } == 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(())
    }
}

impl Drop for Job {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.handle);
        }
    }
}

fn wide(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}
