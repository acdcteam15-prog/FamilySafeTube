package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CelebrationTest {
    // ── confetti and stars ──
    @Test fun pieces_areReproducibleAndInRange() {
        val a = Celebration.pieces(60); val b = Celebration.pieces(60)
        assertEquals(a, b)                                           // no randomness: same every time
        assertEquals(60, a.size); assertTrue(Celebration.pieces(0).isEmpty()); assertTrue(Celebration.pieces(-3).isEmpty())
        for (p in a) {
            assertTrue(p.xFrac in 0f..1f); assertTrue(p.phase in 0f..1f); assertTrue(p.speed in 0.16f..0.38f)
            assertTrue(p.colorIndex in 0 until Celebration.PALETTE_SIZE); assertTrue(p.size in 0.6f..1.4f)
        }
    }

    @Test fun pieces_areVariedAndSomeAreStars() {
        val a = Celebration.pieces(60)
        assertTrue(a.map { it.colorIndex }.toSet().size >= 5)        // several colours
        assertTrue(a.map { it.xFrac }.toSet().size > 50)             // spread across the width
        assertEquals(15, a.count { it.star })                         // every 4th is a star
    }

    @Test fun fall_staysInTheLoopRange_andKeepsFalling() {
        for (p in Celebration.pieces(40)) {
            var t = 0f
            while (t < 30f) { assertTrue(Celebration.fallFraction(p, t) in -0.1001f..1.1501f); t += 0.37f }
        }
        val p = Celebration.pieces(1)[0]
        // moving down: shortly after the same moment it is lower, unless it just wrapped around to the top
        val y0 = Celebration.fallFraction(p, 1f); val y1 = Celebration.fallFraction(p, 1.05f)
        assertTrue(y1 > y0 || y1 < -0.05f)
    }

    @Test fun fall_loopsWithoutAJump() {
        val p = Celebration.pieces(3)[1]
        val cycle = 1.25f / p.speed                                    // seconds for one full pass
        assertEquals(Celebration.fallFraction(p, 2f), Celebration.fallFraction(p, 2f + cycle), 0.001f)
    }

    @Test fun sway_staysOnScreen() {
        for (p in Celebration.pieces(40)) { var t = 0f; while (t < 20f) { assertTrue(Celebration.swayFraction(p, t) in 0f..1f); t += 0.3f } }
    }

    @Test fun starPoints_areAFivePointedStar() {
        val pts = Celebration.starPoints(100f, 200f, 50f)
        assertEquals(10, pts.size)
        assertEquals(100f, pts[0].first, 0.01f); assertEquals(150f, pts[0].second, 0.01f)        // top point straight above the centre
        for ((i, p) in pts.withIndex()) {
            val r = sqrt((p.first - 100f) * (p.first - 100f) + (p.second - 200f) * (p.second - 200f))
            assertEquals(if (i % 2 == 0) 50f else 22.5f, r, 0.01f)                                 // outer, inner, outer ...
        }
    }

    // ── fireworks ──
    @Test fun noBurstBeforeTheStart_andTheFirstGoesOffAtOnce() {
        assertTrue(Celebration.activeBursts(-1f).isEmpty())
        assertEquals(listOf(0 to 0f), Celebration.activeBursts(0f))
    }

    @Test fun bursts_overlap_andEachLivesOnlyItsLifetime() {
        val at1 = Celebration.activeBursts(1.0f)
        assertEquals(listOf(0, 1), at1.map { it.first })              // burst 0 is 1.0 s old, burst 1 is 0.25 s old
        for (t in listOf(0f, 0.4f, 1f, 2.3f, 5f, 17.77f, 60f)) {
            val active = Celebration.activeBursts(t)
            assertTrue("t=$t", active.isNotEmpty())
            for ((_, age) in active) assertTrue(age >= 0f && age < Celebration.SPARK_LIFE_SEC)
            assertTrue(active.size <= 3)                               // never more than ~2 s worth on screen
        }
    }

    @Test fun burstOrigin_isInTheUpperHalf_andColorIsInThePalette() {
        for (n in 0..200) {
            val (x, y) = Celebration.burstOrigin(n)
            assertTrue(x in 0.15f..0.85f); assertTrue(y in 0.10f..0.45f)
            assertTrue(Celebration.burstColor(n) in 0 until Celebration.PALETTE_SIZE)
        }
        assertTrue((0..50).map { Celebration.burstOrigin(it) }.toSet().size > 40)        // not all in the same place
    }

    @Test fun spark_startsAtTheCentreFullyBright_andBurnsOut() {
        val s = Celebration.sparkAt(0, 3, 0f)!!
        assertEquals(0f, s.dx, 0.001f); assertEquals(0f, s.dy, 0.001f); assertEquals(1f, s.alpha, 0.001f); assertEquals(1f, s.sizeFactor, 0.001f)
        assertNull(Celebration.sparkAt(0, 3, Celebration.SPARK_LIFE_SEC)); assertNull(Celebration.sparkAt(0, 3, -0.1f)); assertNull(Celebration.sparkAt(0, 3, 9f))
        assertNotNull(Celebration.sparkAt(0, 3, Celebration.SPARK_LIFE_SEC - 0.01f))
    }

    @Test fun spark_flysOutwardAndFades() {
        for (i in 0 until Celebration.SPARKS_PER_BURST) {
            var lastDist = -1f; var lastAlpha = 2f
            var age = 0f
            while (age < Celebration.SPARK_LIFE_SEC) {
                val s = Celebration.sparkAt(2, i, age)!!
                assertTrue(s.alpha <= lastAlpha + 1e-4f && s.alpha >= 0f)                    // only ever fades
                lastAlpha = s.alpha
                age += 0.05f
            }
            val early = Celebration.sparkAt(2, i, 0.2f)!!; val later = Celebration.sparkAt(2, i, 0.8f)!!
            val d0 = sqrt(early.dx * early.dx + early.dy * early.dy); val d1 = sqrt(later.dx * later.dx + later.dy * later.dy)
            assertTrue("spark $i should be farther out later ($d0 -> $d1)", d1 > d0)
            lastDist = d1; assertTrue(lastDist > 0f)
        }
    }

    @Test fun sparks_goInAllDirections_andSinkWithGravity() {
        val end = (0 until Celebration.SPARKS_PER_BURST).map { Celebration.sparkAt(1, it, 1.2f)!! }
        assertTrue(end.any { it.dx > 0.2f } && end.any { it.dx < -0.2f })                    // left and right
        assertTrue(end.any { it.dy > 0.2f } && end.any { it.dy < 0f })                       // some still above the centre
        val up = Celebration.sparkAt(1, 21, 0.1f)!!                                          // (any spark early on)
        assertTrue(abs(up.dy) < 0.6f)
    }

    // ── the dancing logo ──
    @Test fun dance_hopsUpAndComesBackDown() {
        val ground = Celebration.dance(0f)
        assertEquals(0f, ground.offsetY, 0.001f)
        val top = Celebration.dance(0.5f / 0.9f)                                                // half way through a hop
        assertEquals(-0.30f, top.offsetY, 0.01f)
        for (k in 0..400) assertTrue(Celebration.dance(k * 0.025f).offsetY in -0.3001f..0.0001f)   // never below the floor
    }

    @Test fun dance_squashesOnLanding_andStretchesNeverNegative() {
        val land = Celebration.dance(0f)
        assertTrue(land.scaleY < 1f && land.scaleX > 1f)                                        // squashed when it touches down
        val air = Celebration.dance(0.5f / 0.9f)
        assertEquals(1f, air.scaleX, 0.001f); assertEquals(1f, air.scaleY, 0.001f)
        for (k in 0..400) { val d = Celebration.dance(k * 0.025f); assertTrue(d.scaleX in 0.99f..1.11f && d.scaleY in 0.89f..1.01f) }
    }

    @Test fun dance_wiggles_butOnlyALittle() {
        var min = 99f; var max = -99f
        for (k in 0..400) { val r = Celebration.dance(k * 0.025f).rotationDeg; min = minOf(min, r); max = maxOf(max, r) }
        assertTrue(min < -8f && max > 8f && min >= -9.01f && max <= 9.01f)
    }

    @Test fun dance_repeatsEveryHop() {
        val a = Celebration.dance(1.3f).offsetY; val b = Celebration.dance(1.3f + 1f / 0.9f).offsetY
        assertEquals(a, b, 0.001f)
        assertFalse(Celebration.dance(0.2f) == Celebration.dance(0.7f))
    }
}
