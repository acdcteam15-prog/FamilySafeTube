package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizImageLayoutTest {
    private fun rowOnly(n: Int, w: Float, h: Float): Float {
        // what the OLD layout gave: a square per picture in one row
        return minOf((w - 12f * (n - 1)) / n, h)
    }

    @Test fun phonePortrait_threePictures_stackAndAreMuchBiggerThanBefore() {
        val p = QuizImageLayout.plan(3, 328f, 520f)
        assertEquals(ImageArrangement.COLUMN, p.arrangement)
        val old = rowOnly(3, 328f, 520f)                     // about 101 dp square
        assertTrue("height ${p.heightDp} vs old $old", p.heightDp > old * 1.5f)
        assertTrue("width ${p.widthDp} vs old $old", p.widthDp > old * 2f)
    }

    @Test fun phonePortrait_twoPictures_stackAndAreHuge() {
        val p = QuizImageLayout.plan(2, 328f, 520f)
        assertEquals(ImageArrangement.COLUMN, p.arrangement)
        assertTrue(p.heightDp > 240f && p.widthDp > 300f)
    }

    @Test fun landscape_prefersARow() {
        val p = QuizImageLayout.plan(3, 1000f, 380f)
        assertEquals(ImageArrangement.ROW, p.arrangement)
    }

    @Test fun portraitTablet_threePictures_useAGrid() {
        val p = QuizImageLayout.plan(3, 600f, 700f)
        assertEquals(ImageArrangement.GRID, p.arrangement)
    }

    @Test fun picturesNeverExceedTheirSpace() {
        for (n in 1..4) for (w in listOf(200f, 328f, 600f, 1000f)) for (h in listOf(250f, 520f, 700f, 400f)) {
            val p = QuizImageLayout.plan(n, w, h)
            val gap = QuizImageLayout.GAP_DP
            val (totalW, totalH) = when (p.arrangement) {
                ImageArrangement.ROW -> (p.widthDp * n + gap * (n - 1)) to p.heightDp
                ImageArrangement.COLUMN -> p.widthDp to (p.heightDp * n + gap * (n - 1))
                ImageArrangement.GRID -> {
                    val rows = Math.ceil(n / 2.0).toInt()
                    (p.widthDp * 2 + gap) to (p.heightDp * rows + gap * (rows - 1))
                }
            }
            // only the minimum-size floor may push past a tiny space
            if (w >= 300f && h >= 400f) {
                assertTrue("n=$n w=$w h=$h $p total=${totalW}x$totalH", totalW <= w + 0.5f && totalH <= h + 0.5f)
            }
        }
    }

    @Test fun aspectRatioStaysReasonableSoPhotosAreCroppedNotStretched() {
        for (n in 1..4) for (w in listOf(328f, 600f, 1000f)) for (h in listOf(300f, 520f, 700f)) {
            val p = QuizImageLayout.plan(n, w, h)
            val aspect = p.widthDp / p.heightDp
            assertTrue("aspect $aspect for $p", aspect in 0.79f..1.51f)
        }
    }

    @Test fun neverSmallerThanTheFloor_orLargerThanTheCap() {
        val tiny = QuizImageLayout.plan(3, 50f, 50f)
        assertTrue(tiny.widthDp >= QuizImageLayout.MIN_SIDE && tiny.heightDp >= QuizImageLayout.MIN_SIDE)
        val huge = QuizImageLayout.plan(1, 5000f, 5000f)
        assertTrue(huge.widthDp <= QuizImageLayout.MAX_SIDE && huge.heightDp <= QuizImageLayout.MAX_SIDE)
    }

    @Test fun moreHeightNeverMakesPicturesSmaller() {
        var last = 0f
        for (h in listOf(300f, 400f, 500f, 600f, 700f, 800f)) {
            val a = QuizImageLayout.plan(3, 360f, h).area
            assertTrue("area $a < $last at h=$h", a >= last - 0.01f)
            last = a
        }
    }

    @Test fun singlePictureFillsTheSpace() {
        val p = QuizImageLayout.plan(1, 328f, 520f)
        assertTrue(p.widthDp >= 300f && p.heightDp >= 400f)
    }

    // ── content-aware sizing (tall letter cards must be shown whole) ──
    @Test fun knownAspect_framesHaveExactlyThatShape_soNothingIsCropped() {
        for (aspect in listOf(0.5f, 0.67f, 1f, 1.5f, 2f)) for (n in 2..3) for (w in listOf(328f, 600f, 1000f)) for (h in listOf(400f, 520f, 700f)) {
            val p = QuizImageLayout.plan(n, w, h, aspect = aspect)
            assertEquals("aspect $aspect n=$n $w x $h -> $p", aspect, p.widthDp / p.heightDp, 0.02f)
        }
    }

    @Test fun tallPictures_onAPhone_chooseAGridAndStayBig() {
        // the real case: three tall letter cards on a normal phone
        val tall = QuizImageLayout.plan(3, 328f, 520f, aspect = 0.67f)
        val column = QuizImageLayout.plan(3, 328f, 520f, aspect = 1.5f)       // what a wide photo would get
        assertEquals(ImageArrangement.GRID, tall.arrangement)
        assertTrue("tall frame ${tall.widthDp}x${tall.heightDp}", tall.heightDp > 200f && tall.widthDp > 130f)
        assertTrue(tall.heightDp >= column.heightDp)
    }

    @Test fun squarePictures_use_theLargestSquareThatFits() {
        val p = QuizImageLayout.plan(3, 328f, 520f, aspect = 1f)
        assertEquals(p.widthDp, p.heightDp, 0.5f)
        assertTrue(p.widthDp > 150f)
    }

    @Test fun knownAspect_neverExceedsTheSpace() {
        for (aspect in listOf(0.5f, 0.67f, 1f, 1.5f)) for (n in 1..3) {
            val p = QuizImageLayout.plan(n, 328f, 520f, aspect = aspect)
            val gap = QuizImageLayout.GAP_DP
            val (tw, th) = when (p.arrangement) {
                ImageArrangement.ROW -> (p.widthDp * n + gap * (n - 1)) to p.heightDp
                ImageArrangement.COLUMN -> p.widthDp to (p.heightDp * n + gap * (n - 1))
                ImageArrangement.GRID -> { val rows = Math.ceil(n / 2.0).toInt(); (p.widthDp * 2 + gap) to (p.heightDp * rows + gap * (rows - 1)) }
            }
            assertTrue("aspect=$aspect n=$n $p total=${tw}x$th", tw <= 328.5f && th <= 520.5f)
        }
    }

    @Test fun cropDecision_onlyWhenLittleIsLost() {
        assertTrue(QuizImageLayout.shouldCrop(1.5f, 1.5f))
        assertTrue(QuizImageLayout.shouldCrop(1.5f, 1.3f))          // a slightly different photo: crop is fine
        assertFalse(QuizImageLayout.shouldCrop(0.67f, 1.5f))        // tall letter card in a wide frame: show it whole
        assertFalse(QuizImageLayout.shouldCrop(1.0f, 1.8f))
        assertFalse(QuizImageLayout.shouldCrop(0f, 1.5f))           // unknown shape: never crop
        assertFalse(QuizImageLayout.shouldCrop(1.5f, 0f))
    }

    @Test fun meanAspect_ignoresUnknownAndIsClamped() {
        assertEquals(1.0f, QuizImageLayout.meanAspect(listOf(0.5f, 1.5f)), 0.001f)
        assertEquals(1.2f, QuizImageLayout.meanAspect(listOf(1.2f, 0f, -1f)), 0.001f)
        assertEquals(0f, QuizImageLayout.meanAspect(emptyList()), 0f)
        assertEquals(0f, QuizImageLayout.meanAspect(listOf(0f, 0f)), 0f)
        assertEquals(2.5f, QuizImageLayout.meanAspect(listOf(9f)), 0.001f)
    }

    @Test fun forcedColumn_alwaysStacksOneAboveTheOther_evenWhereARowWouldBeBigger() {
        for (n in 2..3) for ((w, h) in listOf(328f to 520f, 1000f to 380f, 600f to 700f, 800f to 400f)) for (aspect in listOf(0f, 0.67f, 1f, 1.5f)) {
            val p = QuizImageLayout.plan(n, w, h, aspect = aspect, only = ImageArrangement.COLUMN)
            assertEquals("n=$n ${w}x$h aspect=$aspect", ImageArrangement.COLUMN, p.arrangement)
        }
        // on a wide screen the free choice would be a row; the quiz must still stack
        assertEquals(ImageArrangement.ROW, QuizImageLayout.plan(3, 1000f, 380f).arrangement)
        assertEquals(ImageArrangement.COLUMN, QuizImageLayout.plan(3, 1000f, 380f, only = ImageArrangement.COLUMN).arrangement)
    }

    @Test fun zeroOrNegativeCount_isTreatedAsOne() {
        assertEquals(QuizImageLayout.plan(1, 328f, 520f), QuizImageLayout.plan(0, 328f, 520f))
    }
}
