package com.example.familysafetube

/** Pure lockout schedule for wrong parental-gate answers. */
object GateRules {
    const val MAX_ATTEMPTS = 3

    /** Lock duration after the n-th consecutive lockout (0-based): 1 min, 5 min, 15 min, then 1 h. */
    fun lockoutSeconds(lockoutIndex: Int): Int = when {
        lockoutIndex <= 0 -> 60
        lockoutIndex == 1 -> 300
        lockoutIndex == 2 -> 900
        else              -> 3600
    }

    /** Builds the maths question and its answer: a x b + c. Returns (question, answer). */
    fun mathChallenge(random: kotlin.random.Random = kotlin.random.Random.Default): Pair<String, Int> {
        val a = random.nextInt(6, 13)    // 6..12
        val b = random.nextInt(6, 10)    // 6..9
        val c = random.nextInt(10, 41)   // 10..40
        return "$a × $b + $c" to (a * b + c)
    }
}
