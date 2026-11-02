package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {
    private fun fails(text: String): Boolean = try { MiniJson.parse(text); false } catch (_: JsonException) { true }

    @Test fun parsesNestedStructures() {
        @Suppress("UNCHECKED_CAST")
        val m = MiniJson.parse("""{"a":1,"b":[true,false,null,"x"],"c":{"d":-2.5e1}}""") as Map<String, Any?>
        assertEquals(1.0, m["a"])
        assertEquals(listOf(true, false, null, "x"), m["b"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(-25.0, (m["c"] as Map<String, Any?>)["d"])
    }

    @Test fun parsesStringEscapes() {
        assertEquals("a\"b\\c\n\u00e9", MiniJson.parse("\"a\\\"b\\\\c\\n\\u00e9\""))
    }

    @Test fun toleratesWhitespace() {
        assertTrue(MiniJson.parse("  \n { \"k\" :  [ 1 , 2 ] } \t") is Map<*, *>)
    }

    @Test fun rejectsMalformed() {
        assertTrue(fails(""))
        assertTrue(fails("{"))
        assertTrue(fails("""{"a":}"""))
        assertTrue(fails("""{"a":1,}"""))
        assertTrue(fails("""{a:1}"""))
        assertTrue(fails("""[1,2"""))
        assertTrue(fails("""{"a":1} extra"""))
        assertTrue(fails("\"unterminated"))
        assertTrue(fails("tru"))
        assertTrue(fails("""{"a":"\q"}"""))
    }

    @Test fun rejectsExcessiveNesting() {
        assertTrue(fails("[".repeat(60) + "]".repeat(60)))
    }

    @Test fun emptyContainers() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse("{}"))
        assertEquals(emptyList<Any?>(), MiniJson.parse("[]"))
        assertNull(MiniJson.parse("null"))
    }

    @Test fun stringify_roundTripsNastyStrings() {
        val nasty = "quote\" back\\slash\nnewline\ttab \u0001 arabic: أسد 🦁"
        val back = MiniJson.parse(MiniJson.stringify(mapOf("k" to nasty, "n" to 3, "d" to 2.5, "b" to true, "z" to null, "l" to listOf(1, "x"))))
        @Suppress("UNCHECKED_CAST")
        val m = back as Map<String, Any?>
        assertEquals(nasty, m["k"]); assertEquals(3.0, m["n"]); assertEquals(2.5, m["d"]); assertEquals(true, m["b"])
        assertNull(m["z"]); assertEquals(listOf(1.0, "x"), m["l"])
    }

    @Test fun stringify_compactAndStable() {
        assertEquals("""{"a":1,"b":[true,null]}""", MiniJson.stringify(linkedMapOf("a" to 1, "b" to listOf(true, null))))
        assertEquals("{}", MiniJson.stringify(emptyMap<String, Any?>()))
    }
}
