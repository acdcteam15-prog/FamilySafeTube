package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCatalogTest {
    @Test fun knownLabels_haveArabicNames() {
        assertEquals("أسد", QuizCatalog.lookup("Lion")?.ar)
        assertEquals("دب قطبي", QuizCatalog.lookup("Polar bear")?.ar)
        assertEquals("تفاحة", QuizCatalog.lookup("apple")?.ar)
    }

    @Test fun lookup_isCaseAndSpacingTolerant() {
        assertEquals("أسد", QuizCatalog.lookup("  LION ")?.ar)
        assertEquals("دب قطبي", QuizCatalog.lookup("polar_bear")?.ar)
        assertEquals("دب قطبي", QuizCatalog.lookup("Polar   bear")?.ar)
    }

    @Test fun lookup_triesSingularForms() {
        assertEquals("أسد", QuizCatalog.lookup("lions")?.ar)
        assertEquals("تمر", QuizCatalog.lookup("dates")?.ar)
        assertEquals("بطيخ", QuizCatalog.lookup("watermelon")?.ar)
    }

    @Test fun unknownLabel_isNull() {
        assertNull(QuizCatalog.lookup("Quokka"))
        assertNull(QuizCatalog.lookup(""))
    }

    @Test fun animalsWithBundledSounds_pointAtRealSoundKeys() {
        for (label in listOf("dog", "cat", "cow", "sheep", "pig", "rooster", "hen", "chicken", "frog", "crow", "bird", "puppy", "kitten", "lamb")) {
            val key = QuizCatalog.lookup(label)?.soundKey
            assertNotNull("$label should have a sound", key)
            assertTrue("$label -> $key", key in QuizCatalog.SOUND_KEYS)
        }
        assertNull(QuizCatalog.lookup("lion")?.soundKey)      // no bundled lion sound: parent adds one
    }

    @Test fun everySoundKeyIsUsedByAtLeastOneEntry() {
        val used = listOf("dog", "cat", "cow", "sheep", "pig", "rooster", "hen", "frog", "crow", "bird")
            .mapNotNull { QuizCatalog.lookup(it)?.soundKey }.toSet()
        assertEquals(QuizCatalog.SOUND_KEYS, used)
    }

    @Test fun catalogIsNotTiny() {
        assertTrue(QuizCatalog.size >= 80)
    }
}
