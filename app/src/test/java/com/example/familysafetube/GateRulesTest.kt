package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GateRulesTest {

    @Test fun lockout_escalates() {
        assertEquals(60, GateRules.lockoutSeconds(0))
        assertEquals(300, GateRules.lockoutSeconds(1))
        assertEquals(900, GateRules.lockoutSeconds(2))
        assertEquals(3600, GateRules.lockoutSeconds(3))
        assertEquals(3600, GateRules.lockoutSeconds(50))
    }

    private val SUM = Regex("""(\d+) \+ (\d+)""")
    private val DIFF = Regex("""(\d+) − (\d+)""")

    @Test fun math_answerMatchesQuestion() {
        repeat(500) { seed ->
            val (q, answer) = GateRules.mathChallenge(Random(seed))
            val sum = SUM.matchEntire(q)
            val diff = DIFF.matchEntire(q)
            assertTrue("unexpected question format: $q", sum != null || diff != null)
            if (sum != null) {
                val (a, b) = sum.destructured
                assertEquals(a.toInt() + b.toInt(), answer)
            } else {
                val (a, b) = diff!!.destructured
                assertEquals(a.toInt() - b.toInt(), answer)
            }
        }
    }

    @Test fun math_isEasy_oneStepSmallNumbers_neverNegative() {
        var sums = 0; var diffs = 0
        repeat(500) { seed ->
            val (q, answer) = GateRules.mathChallenge(Random(seed))
            SUM.matchEntire(q)?.let {
                sums++
                val (a, b) = it.destructured
                assertTrue(a.toInt() in 2..9); assertTrue(b.toInt() in 2..9)
                assertTrue(answer in 4..18)
            }
            DIFF.matchEntire(q)?.let {
                diffs++
                val (a, b) = it.destructured
                assertTrue(a.toInt() in 10..19); assertTrue(b.toInt() in 2..9)
                assertTrue("answer $answer", answer in 1..17)
            }
        }
        assertTrue("both kinds should appear", sums > 100 && diffs > 100)
    }

    @Test fun math_hasNoMultiplication() {
        repeat(200) { seed -> assertTrue("×" !in GateRules.mathChallenge(Random(seed)).first) }
    }

    // ── which ways the gate offers ──
    @Test fun start_phoneLockFirstWhenOn() {
        assertEquals(GateStart.PHONE, GateRules.start(phoneLock = true, hasPin = true))
        assertEquals(GateStart.PHONE, GateRules.start(phoneLock = true, hasPin = false))
    }

    @Test fun start_withoutPhoneLock_isTheOldBehaviour() {
        assertEquals(GateStart.CHOICE, GateRules.start(phoneLock = false, hasPin = true))
        assertEquals(GateStart.MATH, GateRules.start(phoneLock = false, hasPin = false))
    }

    @Test fun methods_listsEveryWayAndMathsIsAlwaysThere() {
        assertEquals(listOf(GateMethod.PHONE, GateMethod.PIN, GateMethod.MATH), GateRules.methods(true, true))
        assertEquals(listOf(GateMethod.PHONE, GateMethod.MATH), GateRules.methods(true, false))
        assertEquals(listOf(GateMethod.PIN, GateMethod.MATH), GateRules.methods(false, true))
        assertEquals(listOf(GateMethod.MATH), GateRules.methods(false, false))
    }

    @Test fun lockout_blocksPinAndMaths_butNotThePhoneLock() {
        assertTrue(GateRules.gateOpenForAttempts(0L, phoneLock = false))
        assertFalse(GateRules.gateOpenForAttempts(30_000L, phoneLock = false))
        assertTrue(GateRules.gateOpenForAttempts(30_000L, phoneLock = true))
        assertTrue(GateRules.gateOpenForAttempts(0L, phoneLock = true))
    }
}
