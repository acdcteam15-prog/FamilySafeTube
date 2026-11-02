package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizFamilyTest {
    private val mama = FamilyCard("family_1000", "family_1000.jpg", "ماما", "Mama")

    @Test fun ids_haveTheFamilyPrefix() { assertEquals("family_1234", QuizFamilyStore.newId(1234)) }

    @Test fun store_roundTripWithArabicNames() {
        val list = listOf(mama, FamilyCard("family_2000", "family_2000.jpg", "جدّة", "Teta"))
        assertEquals(list, QuizFamilyStore.decode(QuizFamilyStore.encode(list)))
    }

    @Test fun store_skipsBadEntriesAndGarbage() {
        assertTrue(QuizFamilyStore.decode(null).isEmpty()); assertTrue(QuizFamilyStore.decode("{oops").isEmpty())
        val l = QuizFamilyStore.decode("""{"cards":[{"id":"family_1","photo":"a.jpg"},{"id":"notfamily","photo":"b.jpg"},{"id":"family_2"},{"id":"family_1","photo":"dup.jpg"},5]}""")
        assertEquals(listOf("family_1"), l.map { it.id })
        assertEquals("", l.first().nameAr)
    }

    @Test fun cards_useThePhotoAndNames() {
        val c = QuizCards.buildFamily(listOf(mama), "/data/quiz_family", emptyMap(), today = 0L).single()
        assertEquals("/data/quiz_family/family_1000.jpg", (c.art as CardArt.Picture).path)
        assertEquals("ماما", c.nameAr); assertEquals("Mama", c.nameEn)
        assertTrue(c.isFamily); assertFalse(c.needsArabicName); assertFalse(c.hasSound); assertEquals("family", c.category)
    }

    @Test fun cards_fillAMissingNameFromTheOtherLanguage() {
        val onlyEn = QuizCards.buildFamily(listOf(FamilyCard("family_1", "a.jpg", "", "Baba")), "/d", emptyMap(), 0L).single()
        assertEquals("Baba", onlyEn.nameAr)          // so the quiz always has something to say
        val onlyAr = QuizCards.buildFamily(listOf(FamilyCard("family_2", "b.jpg", "بابا", "")), "/d", emptyMap(), 0L).single()
        assertEquals("بابا", onlyAr.nameEn)
    }

    @Test fun cards_takeTheParentsVoiceAndSwitches() {
        val c = QuizCards.buildFamily(
            listOf(mama), "/d",
            mapOf("family_1000" to CardOverride(voiceArFile = "m.snd", enabled = false, focusSinceDay = 50L)), today = 53L
        ).single()
        assertEquals("m.snd", c.voiceArFile); assertFalse(c.enabled); assertTrue(c.focus)
    }

    @Test fun focus_lastsAWeekThenExpires() {
        assertTrue(QuizCards.isFocused(100L, 100L)); assertTrue(QuizCards.isFocused(100L, 107L))
        assertFalse(QuizCards.isFocused(100L, 108L)); assertFalse(QuizCards.isFocused(null, 100L))
        assertFalse(QuizCards.isFocused(100L, 99L))     // a clock set back is not "focused"
    }

    @Test fun pictureCards_getFocusToo_andCardStoreKeepsIt() {
        val c = QuizCards.build(listOf("animals_lion.png" to "/p"), mapOf("animals_lion" to CardOverride(focusSinceDay = 10L)), today = 12L).single()
        assertTrue(c.focus)
        val back = QuizCardStore.decodeOverrides(QuizCardStore.encodeOverrides(mapOf("animals_lion" to CardOverride(focusSinceDay = 10L))))
        assertEquals(10L, back["animals_lion"]?.focusSinceDay)
        assertNull(QuizCardStore.decodeOverrides("""{"cards":{"a_b":{"enabled":false}}}""")["a_b"]?.focusSinceDay)
    }

    @Test fun pictureCardsKeepTheirPictureArt() {
        val c = QuizCards.build(listOf("animals_lion.png" to "/p/animals_lion.png"), emptyMap(), 0L).single()
        assertEquals(CardArt.Picture("/p/animals_lion.png"), c.art)
    }
}
