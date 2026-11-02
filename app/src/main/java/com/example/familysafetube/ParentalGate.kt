package com.example.familysafetube

import android.content.Context

/**
 * Persistent (survives app restarts) failed-attempt counter + lockout for the parental gate.
 * State lives in the settings store, so killing the app does not reset the lockout.
 */
@Suppress("UNUSED_PARAMETER")
object GateGuard {
    private val store get() = AppGraph.settings

    /** Milliseconds of lockout left, 0 if the gate is open for attempts. */
    fun remainingLockMs(c: Context, now: Long = System.currentTimeMillis()): Long {
        val until = store.getOr(Keys.GATE_LOCK_UNTIL, 0L)
        // A lock timestamp absurdly far in the future (clock changed) is capped to the max lock.
        val capped = until.coerceAtMost(now + GateRules.lockoutSeconds(99) * 1000L)
        return (capped - now).coerceAtLeast(0L)
    }

    /** Records a wrong answer. Returns the lock duration in seconds if this attempt triggered a lockout, else 0. */
    fun recordFailure(c: Context, now: Long = System.currentTimeMillis()): Int {
        val fails = store.getOr(Keys.GATE_FAILS, 0) + 1
        if (fails < GateRules.MAX_ATTEMPTS) {
            store.edit { it[Keys.GATE_FAILS] = fails }
            return 0
        }
        val lockouts = store.getOr(Keys.GATE_LOCKOUTS, 0)
        val secs = GateRules.lockoutSeconds(lockouts)
        store.edit {
            it[Keys.GATE_FAILS] = 0
            it[Keys.GATE_LOCKOUTS] = lockouts + 1
            it[Keys.GATE_LOCK_UNTIL] = now + secs * 1000L
        }
        return secs
    }

    fun attemptsLeft(c: Context): Int =
        (GateRules.MAX_ATTEMPTS - store.getOr(Keys.GATE_FAILS, 0)).coerceAtLeast(0)

    fun recordSuccess(c: Context) {
        store.edit {
            it[Keys.GATE_FAILS] = 0
            it[Keys.GATE_LOCKOUTS] = 0
            it[Keys.GATE_LOCK_UNTIL] = 0L
        }
    }
}

/** Stores the parental PIN as salted PBKDF2 (see [PinHasher]). Migrates the old plain-text PIN. */
@Suppress("UNUSED_PARAMETER")
object PinStore {
    private val store get() = AppGraph.settings

    /** Call once at startup: converts a legacy plain-text PIN into a hash and deletes the original. */
    fun migrateLegacy(c: Context) {
        val legacy = store[Keys.PIN_LEGACY] ?: return
        val salt = PinHasher.newSalt()
        val hash = if (legacy.length == 4 && legacy.all { it.isDigit() }) PinHasher.hash(legacy, salt) else null
        store.edit {
            if (hash != null) {
                it[Keys.PIN_SALT] = salt
                it[Keys.PIN_HASH] = hash
            }
            it.remove(Keys.PIN_LEGACY)
        }
    }

    fun hasPin(c: Context): Boolean = store[Keys.PIN_HASH] != null

    fun setPin(c: Context, pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        store.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    fun removePin(c: Context) {
        store.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
        }
    }

    fun verify(c: Context, pin: String): Boolean {
        val salt = store[Keys.PIN_SALT] ?: return false
        val hash = store[Keys.PIN_HASH] ?: return false
        return PinHasher.verify(pin, salt, hash)
    }
}
