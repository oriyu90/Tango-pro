package com.example.tangopro.domain

@OptIn(ExperimentalWasmJsInterop::class)
actual fun normalizeNfkc(value: String): String = js("value.normalize('NFKC')")
