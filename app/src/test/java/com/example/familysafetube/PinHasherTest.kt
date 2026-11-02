package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test fun verify_acceptsCorrectPinOnly() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
        assertFalse(PinHasher.verify("4822", salt, hash))
        assertFalse(PinHasher.verify("", salt, hash))
    }

    @Test fun hash_isDeterministicForSameSalt() {
        val salt = PinHasher.newSalt()
        assertEquals(PinHasher.hash("0000", salt), PinHasher.hash("0000", salt))
    }

    @Test fun hash_differsAcrossSalts() {
        assertNotEquals(PinHasher.hash("1234", PinHasher.newSalt()), PinHasher.hash("1234", PinHasher.newSalt()))
    }

    @Test fun hash_doesNotContainThePin() {
        val salt = PinHasher.newSalt()
        assertFalse(PinHasher.hash("1234", salt).contains("1234"))
    }
}
