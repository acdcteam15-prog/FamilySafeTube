package com.example.familysafetube

import org.junit.Assert.assertEquals
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

    @Test fun math_answerMatchesQuestion() {
        repeat(200) { seed ->
            val (q, answer) = GateRules.mathChallenge(Random(seed))
            val m = Regex("""(\d+) × (\d+) \+ (\d+)""").matchEntire(q)
            assertTrue("unexpected question format: $q", m != null)
            val (a, b, c) = m!!.destructured
            assertEquals(a.toInt() * b.toInt() + c.toInt(), answer)
        }
    }

    @Test fun math_operandsStayInRange() {
        repeat(200) { seed ->
            val (q, _) = GateRules.mathChallenge(Random(seed))
            val (a, b, c) = Regex("""(\d+) × (\d+) \+ (\d+)""").matchEntire(q)!!.destructured
            assertTrue(a.toInt() in 6..12)
            assertTrue(b.toInt() in 6..9)
            assertTrue(c.toInt() in 10..40)
        }
    }
}
