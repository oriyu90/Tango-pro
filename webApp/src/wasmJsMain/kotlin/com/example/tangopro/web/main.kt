package com.example.tangopro.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.MainScope

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    registerServiceWorker()
    acquireDatabaseLock { acquired ->
        ComposeViewport {
            if (acquired) {
                TangoWebApp(WebAppState(WebRepository.open(), MainScope()))
            } else {
                DatabaseLockedScreen()
            }
        }
        removeLoading()
    }
}
