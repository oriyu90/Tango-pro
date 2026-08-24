package com.example.tangopro.sqlite

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
actual fun createSQLiteWasmDriver(): WebWorkerSQLiteDriver = WebWorkerSQLiteDriver(createWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun createWorker(): Worker =
    js("new Worker(new URL('tango-pro-sqlite-wasm-worker/worker.js', import.meta.url), { type: 'module' })")
