package com.example.familysafetube.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private fun info(build: Int) = ReleaseInfo("1.0.0", build, "", false, "https://x/a.apk", null, 1L, null, null, null)
    private val hour = 3_600_000L

    @Test fun `a check is due after six hours, never before`() {
        val now = 100 * hour
        assertTrue(UpdatePolicy.checkDue(now, now - 7 * hour))
        assertFalse(UpdatePolicy.checkDue(now, now - 5 * hour))
        assertFalse(UpdatePolicy.checkDue(now, now - 6 * hour))        // exactly six hours is not yet "more than"
        assertTrue(UpdatePolicy.checkDue(now, now - 6 * hour - 1))
    }

    @Test fun `a check that never happened, or whose clock went backwards, is due`() {
        assertTrue(UpdatePolicy.checkDue(1_000L, 0L))
        assertTrue(UpdatePolicy.checkDue(1_000L, 5_000L))              // last check "in the future": the clock was set back
    }

    @Test fun `one notification per build and none for a skipped build`() {
        assertTrue(UpdatePolicy.shouldNotify(build = 105, skippedBuild = 0, notifiedBuild = 0))
        assertFalse(UpdatePolicy.shouldNotify(build = 105, skippedBuild = 105, notifiedBuild = 0))
        assertFalse(UpdatePolicy.shouldNotify(build = 105, skippedBuild = 0, notifiedBuild = 105))
        assertTrue(UpdatePolicy.shouldNotify(build = 106, skippedBuild = 105, notifiedBuild = 105))   // a newer build shows again
    }

    @Test fun `background download needs permission, an unmetered network and no download yet`() {
        assertTrue(UpdatePolicy.shouldAutoDownload(autoDownload = true, installAllowed = true, metered = false, alreadyDownloaded = false))
        assertFalse(UpdatePolicy.shouldAutoDownload(false, true, false, false))        // switched off
        assertFalse(UpdatePolicy.shouldAutoDownload(true, false, false, false))        // install not allowed yet
        assertFalse(UpdatePolicy.shouldAutoDownload(true, true, true, false))          // mobile data
        assertFalse(UpdatePolicy.shouldAutoDownload(true, true, false, true))          // already there
    }

    @Test fun `the updated message shows once, only after a real update`() {
        assertTrue(UpdatePolicy.justUpdated(lastSeenBuild = 101, currentBuild = 102))
        assertFalse(UpdatePolicy.justUpdated(102, 102))                // same build
        assertFalse(UpdatePolicy.justUpdated(0, 102))                  // first ever launch: nothing to announce
        assertFalse(UpdatePolicy.justUpdated(103, 102))                // downgraded
    }

    @Test fun `the banner hides a skipped build and shows a newer one`() {
        assertNull(UpdatePolicy.bannerRelease(null, 0))
        assertNull(UpdatePolicy.bannerRelease(info(105), 105))
        assertEquals(106, UpdatePolicy.bannerRelease(info(106), 105)!!.build)
        assertEquals(105, UpdatePolicy.bannerRelease(info(105), 0)!!.build)
    }

    @Test fun `only a real owner slash repo counts as configured`() {
        assertTrue(UpdatePolicy.isConfigured("bader/FamilySafeTube"))
        assertFalse(UpdatePolicy.isConfigured("CHANGE_ME/FamilySafeTube"))
        assertFalse(UpdatePolicy.isConfigured(""))
        assertFalse(UpdatePolicy.isConfigured("   "))
        assertFalse(UpdatePolicy.isConfigured("justaname"))
        assertFalse(UpdatePolicy.isConfigured("a/b/c"))
        assertFalse(UpdatePolicy.isConfigured("owner/"))
    }

    @Test fun `urls and tokens`() {
        assertEquals("https://api.github.com/repos/o/r/releases/latest", UpdatePolicy.latestReleaseUrl("o/r"))
        assertTrue(UpdatePolicy.isSecureUrl("https://github.com/x"))
        assertFalse(UpdatePolicy.isSecureUrl("http://github.com/x"))
        assertFalse(UpdatePolicy.isSecureUrl("ftp://x"))
        assertEquals("abc", UpdatePolicy.cleanToken("  abc \n"))
        assertEquals("", UpdatePolicy.cleanToken("   "))
    }

    @Test fun `the background schedule is twice a day`() {
        assertEquals(12L, UpdatePolicy.BACKGROUND_CHECK_HOURS)
    }
}
