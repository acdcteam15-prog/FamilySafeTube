package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCardStoreTest {
    @Test fun overrides_roundTripIncludingArabicText() {
        val src = mapOf(
            "animals_lion" to CardOverride(nameAr = "أسد \"الغابة\"", soundFile = "lion.snd", voiceArFile = "lion_ar.snd"),
            "animals_dog" to CardOverride(enabled = false, useBuiltInSound = false, nameEn = "Doggy")
        )
        assertEquals(src, QuizCardStore.decodeOverrides(QuizCardStore.encodeOverrides(src)))
    }

    @Test fun defaultOverrides_arentStored() {
        val json = QuizCardStore.encodeOverrides(mapOf("animals_cat" to CardOverride()))
        assertFalse(json.contains("animals_cat"))
        assertTrue(QuizCardStore.decodeOverrides(json).isEmpty())
    }

    @Test fun corruptOrEmptyInput_readsAsNoOverrides() {
        assertTrue(QuizCardStore.decodeOverrides(null).isEmpty())
        assertTrue(QuizCardStore.decodeOverrides("").isEmpty())
        assertTrue(QuizCardStore.decodeOverrides("{not json").isEmpty())
        assertTrue(QuizCardStore.decodeOverrides("[1,2]").isEmpty())
        assertTrue(QuizCardStore.decodeOverrides("""{"cards": 5}""").isEmpty())
    }

    @Test fun wrongTypesAndUnknownFields_areIgnoredNotFatal() {
        val m = QuizCardStore.decodeOverrides("""{"cards":{"a_b":{"enabled":"yes","name_ar":7,"sound":"x.snd","future":1},"bad":3}}""")
        assertEquals(CardOverride(enabled = true, nameAr = null, soundFile = "x.snd"), m["a_b"])
        assertFalse("bad" in m)
    }

    @Test fun stats_roundTrip() {
        val s = mapOf("animals_lion" to CardStats(5, 3, 2), "veg&fruits_apple" to CardStats(1, 1, 0))
        assertEquals(s, QuizCardStore.decodeStats(QuizCardStore.encodeStats(s)))
    }

    @Test fun stats_corruptEntriesAreSkipped() {
        val m = QuizCardStore.decodeStats("""{"a":[1,2,3],"b":[1,2],"c":["x",1,1],"d":[-4,0,0]}""")
        assertEquals(CardStats(1, 2, 3), m["a"])
        assertFalse("b" in m); assertFalse("c" in m)
        assertEquals(CardStats(0, 0, 0), m["d"])          // negatives clamp to 0
        assertTrue(QuizCardStore.decodeStats("garbage").isEmpty())
    }
}
