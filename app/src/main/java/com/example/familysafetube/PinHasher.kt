package com.example.familysafetube

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the parental PIN. Pure JVM so it is unit-testable.
 *
 * A 4-digit PIN only has 10,000 possibilities, so hashing cannot stop a determined offline attack.
 * What it does do is stop the PIN being readable in plain text from a backup or a rooted
 * SharedPreferences dump. The real brute-force protection is the persistent lockout in [GateRules].
 */
object PinHasher {
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(pin: String, saltB64: String): String {
        val salt = Base64.getDecoder().decode(saltB64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(pin: String, saltB64: String, expectedHashB64: String): Boolean {
        val actual = hash(pin, saltB64).toByteArray()
        return MessageDigest.isEqual(actual, expectedHashB64.toByteArray())
    }
}
