package com.example.tangopro.web

import kotlin.test.Test
import kotlin.test.assertTrue

class BrowserPlatformTest {
    @Test
    fun externalWebsitesOpenInIsolatedNewTabs() {
        assertTrue(capturesExternalWebsiteNavigation(::openExternalWebsite))
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(action) => {
    const originalOpen = window.open;
    const calls = [];
    window.open = (...args) => { calls.push(args); return null; };
    try {
        action('https://studio-rizi.pages.dev/');
        action('https://studio-rizi.pages.dev/projects/tango-pro/');
        return calls.length === 2 &&
            calls[0][0] === 'https://studio-rizi.pages.dev/' &&
            calls[1][0] === 'https://studio-rizi.pages.dev/projects/tango-pro/' &&
            calls.every(call => call[1] === '_blank' && call[2] === 'noopener,noreferrer');
    } finally {
        window.open = originalOpen;
    }
}""")
private external fun capturesExternalWebsiteNavigation(action: (String) -> Unit): Boolean
