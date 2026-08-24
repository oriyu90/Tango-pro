package com.example.tangopro.sqlite

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver

expect fun createSQLiteWasmDriver(): WebWorkerSQLiteDriver
