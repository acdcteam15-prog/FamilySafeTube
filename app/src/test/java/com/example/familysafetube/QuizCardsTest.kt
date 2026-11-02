package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCardsTest {
    private fun pics(vararg names: String) = names.map { it to "/files/$it" }

    @Test fun id_isLowercasedFileNameWithoutExtension() {
        assertEquals("animals_lion", QuizCards.idFromFileName("animals_lion.png"))
        assertEquals("veg&fruits_apple", QuizCards.idFromFileName("Veg&Fruits_Apple.JPG"))
    }

    @Test fun defaults_comeFromTheCatalog() {
        val c = QuizCards.build(pics("animals_dog.png"), emptyMap()).single()
        assertEquals("كلب", c.nameAr)
        assertEquals("Dog", c.nameEn)
        assertEquals("dog", c.builtInSoundKey)
        assertTrue(c.hasSound); assertTrue(c.enabled); assertFalse(c.needsArabicName)
    }

    @Test fun unknownLabel_fallsBackToEnglishAndAsksForAnArabicName() {
        val c = QuizCards.build(pics("animals_quokka.png"), emptyMap()).single()
        assertEquals("Quokka", c.nameAr)
        assertTrue(c.needsArabicName)
        assertFalse(c.hasSound)
    }

    @Test fun overrides_replaceNamesAndClearTheArabicWarning() {
        val c = QuizCards.build(
            pics("animals_quokka.png"),
            mapOf("animals_quokka" to CardOverride(nameAr = "  كوكا ", nameEn = "Quokka!"))
        ).single()
        assertEquals("كوكا", c.nameAr)
        assertEquals("Quokka!", c.nameEn)
        assertFalse(c.needsArabicName)
    }

    @Test fun blankOverrideNames_areIgnored() {
        val c = QuizCards.build(pics("animals_lion.png"), mapOf("animals_lion" to CardOverride(nameAr = "   "))).single()
        assertEquals("أسد", c.nameAr)
    }

    @Test fun parentsSound_beatsTheBuiltInOne() {
        val c = QuizCards.build(pics("animals_dog.png"), mapOf("animals_dog" to CardOverride(soundFile = "dog_mine.snd"))).single()
        assertEquals("dog_mine.snd", c.soundFile)
        assertNull(c.builtInSoundKey)
    }

    @Test fun builtInSound_canBeSwitchedOff() {
        val c = QuizCards.build(pics("animals_dog.png"), mapOf("animals_dog" to CardOverride(useBuiltInSound = false))).single()
        assertNull(c.builtInSoundKey); assertFalse(c.hasSound)
    }

    @Test fun disabledCard_staysInTheListButIsMarkedDisabled() {
        val c = QuizCards.build(pics("animals_dog.png"), mapOf("animals_dog" to CardOverride(enabled = false))).single()
        assertFalse(c.enabled)
    }

    @Test fun arabicFileNames_areTheirOwnArabicName_andNeedNothing() {
        // a picture from the Drive letters folder, named "<letter> - <word>"
        val c = QuizCards.build(pics("letters_ميم - موزة.png"), emptyMap()).single()
        assertEquals("ميم - موزة", c.nameAr)
        assertFalse(c.needsArabicName)
    }

    @Test fun containsArabic() {
        assertTrue(QuizText.containsArabic("موزة")); assertTrue(QuizText.containsArabic("Banana ب"))
        assertFalse(QuizText.containsArabic("Banana")); assertFalse(QuizText.containsArabic("")); assertFalse(QuizText.containsArabic("123 - ok"))
    }

    @Test fun cardsAreSortedAndCarryTheirCategory() {
        val cards = QuizCards.build(pics("veg&fruits_apple.png", "animals_dog.png", "animals_cat.png"), emptyMap())
        assertEquals(listOf("animals_cat", "animals_dog", "veg&fruits_apple"), cards.map { it.id })
        assertEquals("veg&fruits", cards.last().category)
    }

    @Test fun audioSlots_readAndWriteTheRightField() {
        var o = CardOverride()
        o = AudioSlot.SOUND.with(o, "s.snd"); o = AudioSlot.VOICE_AR.with(o, "a.snd"); o = AudioSlot.VOICE_EN.with(o, "e.snd")
        assertEquals("s.snd", o.soundFile); assertEquals("a.snd", o.voiceArFile); assertEquals("e.snd", o.voiceEnFile)
        assertEquals("s.snd", AudioSlot.SOUND.fileOf(o)); assertEquals("a.snd", AudioSlot.VOICE_AR.fileOf(o)); assertEquals("e.snd", AudioSlot.VOICE_EN.fileOf(o))
        assertNull(AudioSlot.SOUND.fileOf(AudioSlot.SOUND.with(o, null)))
        assertEquals("a.snd", AudioSlot.VOICE_AR.fileOf(AudioSlot.SOUND.with(o, null)))   // other slots untouched
    }

    @Test fun slotKeys_areFileNameSafe() {
        assertTrue(AudioSlot.values().all { it.key.matches(Regex("[a-z_]+")) })
        assertEquals(3, AudioSlot.values().map { it.key }.toSet().size)
    }

    @Test fun withNames_storesOnlyRealChanges() {
        val base = CardOverride(soundFile = "x.snd")
        val same = base.withNames("أسد", "Lion", defaultEnglish = "Lion", defaultArabic = "أسد")
        assertNull(same.nameAr); assertNull(same.nameEn); assertEquals("x.snd", same.soundFile)
        val changed = base.withNames("  ليث ", "Leo", "Lion", "أسد")
        assertEquals("ليث", changed.nameAr); assertEquals("Leo", changed.nameEn)
        val blank = base.withNames("   ", "", "Lion", null)
        assertNull(blank.nameAr); assertNull(blank.nameEn)
    }

    @Test fun editedCard_endsUpDefaultAgainWhenEverythingIsReverted() {
        val o = CardOverride().withNames("أسد", "Lion", "Lion", "أسد")
        assertTrue(o.isDefault)
    }
}
