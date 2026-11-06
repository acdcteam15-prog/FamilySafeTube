package com.example.familysafetube

/** What the parental gate shows first. */
enum class GateStart { PHONE, CHOICE, MATH }

/** One way a parent can prove "I am a parent". */
enum class GateMethod { PHONE, PIN, MATH }

/** Pure rules of the parental gate: the lockout schedule, the maths question, and which ways to offer. */
object GateRules {
    const val MAX_ATTEMPTS = 3

    /** Lock duration after the n-th consecutive lockout (0-based): 1 min, 5 min, 15 min, then 1 h. */
    fun lockoutSeconds(lockoutIndex: Int): Int = when {
        lockoutIndex <= 0 -> 60
        lockoutIndex == 1 -> 300
        lockoutIndex == 2 -> 900
        else              -> 3600
    }

    /**
     * Builds the maths question and its answer. It is deliberately easy (one step, small numbers):
     * adding two single digits ("7 + 5") or taking a single digit away from 10..19 ("14 − 6").
     * The answer is never negative. Returns (question, answer).
     */
    fun mathChallenge(random: kotlin.random.Random = kotlin.random.Random.Default): Pair<String, Int> =
        if (random.nextBoolean()) {
            val a = random.nextInt(2, 10)    // 2..9
            val b = random.nextInt(2, 10)    // 2..9
            "$a + $b" to (a + b)
        } else {
            val a = random.nextInt(10, 20)   // 10..19
            val b = random.nextInt(2, 10)    // 2..9
            "$a − $b" to (a - b)
        }

    /** The first screen of the gate: the phone-lock prompt when it can be used, else the PIN/maths choice when a PIN is set, else maths. */
    fun start(phoneLock: Boolean, hasPin: Boolean): GateStart = when {
        phoneLock -> GateStart.PHONE
        hasPin -> GateStart.CHOICE
        else -> GateStart.MATH
    }

    /** The ways listed on the choice screen, in order. The maths fallback is always there (it is the PIN-recovery path). */
    fun methods(phoneLock: Boolean, hasPin: Boolean): List<GateMethod> = buildList {
        if (phoneLock) add(GateMethod.PHONE)
        if (hasPin) add(GateMethod.PIN)
        add(GateMethod.MATH)
    }

    /**
     * Wrong PIN / maths answers lock the PIN and maths for a while. The phone lock has its own protection
     * (the phone locks it out after a few wrong tries), so a child's wrong guesses must not shut the parent out of it.
     */
    fun gateOpenForAttempts(lockMsLeft: Long, phoneLock: Boolean): Boolean = lockMsLeft <= 0L || phoneLock
}
