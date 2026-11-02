package com.example.familysafetube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    private val ids = setOf("dQw4w9WgXcQ", "abcdefghijk")

    private fun ok(url: String, gesture: Boolean) = NavigationPolicy.isAllowed(url, gesture, ids)

    @Test fun tap_onCuratedShort_allowed() {
        assertTrue(ok("https://www.youtube.com/shorts/dQw4w9WgXcQ", true))
        assertTrue(ok("https://m.youtube.com/shorts/abcdefghijk?feature=share", true))
    }

    @Test fun tap_onShortNotInList_blocked() {
        assertFalse(ok("https://www.youtube.com/shorts/zzzzzzzzzzz", true))
    }

    @Test fun tap_onChannelWatchOrHome_blocked() {
        assertFalse(ok("https://www.youtube.com/@somechannel", true))
        assertFalse(ok("https://www.youtube.com/watch?v=dQw4w9WgXcQ", true))
        assertFalse(ok("https://www.youtube.com/", true))
        assertFalse(ok("https://www.youtube.com/results?search_query=x", true))
    }

    @Test fun tap_toForeignSite_blocked() {
        assertFalse(ok("https://example.com/", true))
        assertFalse(ok("https://evil-youtube.com/shorts/dQw4w9WgXcQ", true))
        assertFalse(ok("https://youtube.com.evil.io/shorts/dQw4w9WgXcQ", true))
    }

    @Test fun nonHttps_blocked() {
        assertFalse(ok("http://www.youtube.com/shorts/dQw4w9WgXcQ", true))
        assertFalse(ok("intent://scan/#Intent;scheme=zxing;end", false))
        assertFalse(ok("javascript:alert(1)", false))
    }

    @Test fun pageRedirects_stayOnYoutubeOrGoogle() {
        assertTrue(ok("https://consent.youtube.com/m?continue=x", false))
        assertTrue(ok("https://www.youtube.com/shorts/dQw4w9WgXcQ", false))
        assertFalse(ok("https://ads.example.com/click", false))
    }

    @Test fun consentScreen_tapAllowed() {
        assertTrue(ok("https://consent.youtube.com/save", true))
        assertTrue(ok("https://consent.google.com/save", true))
    }

    @Test fun malformedUrl_blocked() {
        assertFalse(ok("not a url", true))
        assertFalse(ok("", false))
    }
}
