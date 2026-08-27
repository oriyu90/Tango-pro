package com.example.tangopro.web

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(callback) => window.tangoProBridge.acquireDatabaseLock(callback)")
external fun acquireDatabaseLock(callback: (Boolean) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.tangoProBridge.registerServiceWorker()")
external fun registerServiceWorker()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(callback) => window.tangoProBridge.requestPersistentStorage(callback)")
external fun requestPersistentStorage(callback: (Boolean) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(accept, callback) => window.tangoProBridge.pickTextFile(accept, callback)")
external fun pickTextFile(accept: String, callback: (String, String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(callback) => window.tangoProBridge.pickStudyArchive(callback)")
external fun pickStudyArchive(callback: (String, String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(filename, entriesJson) => window.tangoProBridge.createStudyArchive(filename, entriesJson)")
external fun createStudyArchive(filename: String, entriesJson: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(text, callback) => crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)).then(hash => callback(Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join(''), '')).catch(error => callback('', String(error?.message || error)))")
private external fun directHashTextAsync(text: String, callback: (String, String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(filename, text, mime) => window.tangoProBridge.downloadText(filename, text, mime)")
external fun downloadText(filename: String, text: String, mime: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(filename, text, mime) => window.tangoProBridge.shareTextFile(filename, text, mime)")
external fun shareTextFile(filename: String, text: String, mime: String)

// Run directly from the button click so browsers retain the user activation.
// Keep the study tab open and prevent the destination from accessing its opener.
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(url) => { window.open(url, '_blank', 'noopener,noreferrer'); }")
external fun openExternalWebsite(url: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(text, language, volume) => window.tangoProBridge.speak(text, language, volume)")
external fun speak(text: String, language: String, volume: Double)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(correct, volume) => window.tangoProBridge.playTone(correct, volume)")
external fun playTone(correct: Boolean, volume: Double)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(key) => window.tangoProBridge.getSetting(key)")
external fun getSetting(key: String): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(key, value) => window.tangoProBridge.setSetting(key, value)")
external fun setSetting(key: String, value: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.tangoProBridge.removeLoading()")
external fun removeLoading()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(fileName, callback) => window.tangoProBridge.loadBundledText(fileName, callback)")
external fun loadBundledText(fileName: String, callback: (String, String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
external fun epochMillis(): Double

suspend fun fetchBundledCsv(fileName: String): String = suspendCoroutine { continuation ->
    loadBundledText(fileName) { text, error ->
        if (error.isBlank()) continuation.resume(text)
        else continuation.resumeWithException(IllegalStateException("組み込み単語帳を取得できませんでした: $error"))
    }
}

suspend fun sha256(text: String): String = suspendCoroutine { continuation ->
    directHashTextAsync(text) { hash, error ->
        if (error.isBlank()) continuation.resume(hash)
        else continuation.resumeWithException(IllegalStateException("SHA-256を計算できませんでした: $error"))
    }
}
