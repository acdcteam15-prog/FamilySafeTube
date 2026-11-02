package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhonicsTest {
    @Test fun arabicInitial_basics() {
        assertEquals("meem", Phonics.arabicInitialKey("موزة"))
        assertEquals("alif", Phonics.arabicInitialKey("أسد")); assertEquals("alif", Phonics.arabicInitialKey("إوزة"))
        assertEquals("alif", Phonics.arabicInitialKey("آلة")); assertEquals("baa", Phonics.arabicInitialKey("بطة"))
        assertEquals("kaf", Phonics.arabicInitialKey("كلب")); assertEquals("yaa", Phonics.arabicInitialKey("يد"))
        assertEquals("dal", Phonics.arabicInitialKey("دب قطبي"))
    }

    @Test fun arabicInitial_ignoresVowelMarksAndTheArticle() {
        assertEquals("meem", Phonics.arabicInitialKey("مَوزة"))                 // with fatha
        assertEquals("faa", Phonics.arabicInitialKey("الفيل"))                  // article "ال" skipped
        assertEquals("alif", Phonics.arabicInitialKey("ال"))                     // too short to be an article: it IS the word, so alif
        assertEquals("lam", Phonics.arabicInitialKey("اللبن"))                   // article skipped -> the real first letter
        assertEquals("meem", Phonics.arabicInitialKey("  موز "))
    }

    @Test fun arabicInitial_nonArabicIsNull() {
        assertNull(Phonics.arabicInitialKey("Banana")); assertNull(Phonics.arabicInitialKey("")); assertNull(Phonics.arabicInitialKey("123"))
    }

    @Test fun everyArabicDeckLetterIsReachable() {
        val deckKeys = QuizDecks.deck(DeckId.AR_LETTERS).map { it.id.substringAfter('_') }.toSet()
        val glyphs = QuizDecks.deck(DeckId.AR_LETTERS).map { (it.art as CardArt.Glyph).text }
        for (g in glyphs) {
            val key = Phonics.arabicInitialKey(g)
            assertNotNull("glyph $g has no key", key); assertTrue("key $key not a deck letter", key in deckKeys)
        }
    }

    @Test fun englishInitial() {
        assertEquals("b", Phonics.englishInitialKey("Banana")); assertEquals("a", Phonics.englishInitialKey("  apple"))
        assertNull(Phonics.englishInitialKey("موزة")); assertNull(Phonics.englishInitialKey("1 two"))
    }

    @Test fun letterCardIdsMatchTheDecks() {
        assertTrue(QuizDecks.deck(DeckId.AR_LETTERS).any { it.id == Phonics.letterCardId(QuizLang.AR, "meem") })
        assertTrue(QuizDecks.deck(DeckId.EN_LETTERS).any { it.id == Phonics.letterCardId(QuizLang.EN, "b") })
    }

    // ── question building ──
    private fun obj(file: String, nameAr: String, nameEn: String, enabled: Boolean = true) =
        QuizCard("animals_$file", "/p/$file.png", nameEn, nameAr, nameEn, enabled, false, null, null, null, null)

    private val objects = listOf(
        obj("banana", "موزة", "Banana"), obj("lion", "أسد", "Lion"), obj("cat", "قطة", "Cat"), obj("dog", "كلب", "Dog"),
        obj("apple", "تفاحة", "Apple"), obj("monkey", "قرد", "Monkey"), obj("mango", "مانجو", "Mango")
    )
    private val arLetters = QuizDecks.deck(DeckId.AR_LETTERS)
    private val enLetters = QuizDecks.deck(DeckId.EN_LETTERS)
    private val allLetters = arLetters + enLetters

    @Test fun letterToWord_targetStartsWithTheLetter_andNoDistractorDoes() {
        repeat(300) { seed ->
            val q = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 3, random = Random(seed), kind = QuizMode.LETTER_TO_WORD)!!
            assertEquals(QuizMode.LETTER_TO_WORD, q.mode)
            val key = Phonics.arabicInitialKey(q.target.nameAr)
            assertEquals(Phonics.letterCardId(QuizLang.AR, key!!), q.promptCard!!.id)
            val wrong = q.options.filter { it.id != q.target.id }
            assertTrue(wrong.none { Phonics.arabicInitialKey(it.nameAr) == key })      // no second right answer
            assertEquals(3, q.options.size); assertEquals(3, q.options.map { Phonics.arabicInitialKey(it.nameAr) }.distinct().size)
            assertTrue(q.target in q.options)
        }
    }

    @Test fun wordToLetter_optionsAreDistinctLetters_andTheTargetIsTheFirstLetter() {
        repeat(300) { seed ->
            val q = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 3, random = Random(seed), kind = QuizMode.WORD_TO_LETTER)!!
            assertEquals(QuizMode.WORD_TO_LETTER, q.mode)
            val obj = q.stimulus!!
            assertEquals(Phonics.letterCardId(QuizLang.AR, Phonics.arabicInitialKey(obj.nameAr)!!), q.target.id)
            assertEquals(3, q.options.map { it.id }.distinct().size)
            assertTrue(q.options.all { it.id.startsWith("arletters_") && it.art is CardArt.Glyph })
            assertTrue(q.target in q.options); assertEquals(obj, q.learnedCard)
        }
    }

    @Test fun english_usesEnglishLettersAndNames() {
        val q = PhonicsEngine.pick(objects, allLetters, QuizLang.EN, 3, random = Random(1), kind = QuizMode.WORD_TO_LETTER)!!
        assertTrue(q.options.all { it.id.startsWith("enletters_") })
        assertEquals(Phonics.letterCardId(QuizLang.EN, Phonics.englishInitialKey(q.stimulus!!.nameEn)!!), q.target.id)
    }

    @Test fun twoPictureQuestions_whenAskedForTwoOptions() {
        val q = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 2, random = Random(3))!!
        assertEquals(2, q.options.size)
    }

    @Test fun disabledAndLetterlessCardsAreNeverUsed() {
        val cards = listOf(obj("banana", "موزة", "Banana", enabled = false), obj("lion", "أسد", "Lion"), obj("cat", "قطة", "Cat"), obj("x", "???", "Zork"))
        repeat(100) { seed ->
            val q = PhonicsEngine.pick(cards, allLetters, QuizLang.AR, 3, random = Random(seed), kind = QuizMode.LETTER_TO_WORD)!!
            assertTrue(q.options.none { it.nameEn == "Banana" || it.nameEn == "Zork" })
        }
    }

    @Test fun notEnoughData_givesNull() {
        assertNull(PhonicsEngine.pick(listOf(obj("banana", "موزة", "Banana")), allLetters, QuizLang.AR, 3))            // one letter only
        assertNull(PhonicsEngine.pick(objects, emptyList(), QuizLang.AR, 3))                                              // no letter tiles
        assertNull(PhonicsEngine.pick(emptyList(), allLetters, QuizLang.AR, 3))
        assertFalse(PhonicsEngine.isPossible(listOf(obj("banana", "موزة", "Banana")), allLetters, QuizLang.AR))
        assertTrue(PhonicsEngine.isPossible(objects, allLetters, QuizLang.AR))
    }

    @Test fun recentlyAskedObjectsAreSkippedWhenThereIsChoice() {
        val recent = listOf("animals_banana", "animals_lion", "animals_cat", "animals_dog")
        repeat(100) { seed ->
            val q = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 3, recent, Random(seed), QuizMode.LETTER_TO_WORD)!!
            assertTrue(q.target.id !in recent)
        }
    }

    // ── audio plans for the new modes ──
    @Test fun audioPlans() {
        val l2w = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 3, random = Random(5), kind = QuizMode.LETTER_TO_WORD)!!
        val ask = QuizAudioPlan.promptFor(l2w, QuizLang.AR)
        assertEquals(listOf<AudioStep>(AudioStep.Tts(l2w.promptCard!!.nameAr, QuizLang.AR)), ask)          // only the LETTER is said
        val reveal = QuizAudioPlan.revealFor(l2w, QuizLang.AR)
        assertEquals(AudioStep.Tts(l2w.promptCard!!.nameAr, QuizLang.AR), reveal[0])                          // "ميم" then the word
        assertEquals(AudioStep.Tts(l2w.target.nameAr, QuizLang.AR), reveal[1])
        assertEquals(ask, QuizAudioPlan.hintFor(l2w, QuizLang.AR))

        val w2l = PhonicsEngine.pick(objects, allLetters, QuizLang.AR, 3, random = Random(5), kind = QuizMode.WORD_TO_LETTER)!!
        assertEquals(listOf<AudioStep>(AudioStep.Tts(w2l.stimulus!!.nameAr, QuizLang.AR)), QuizAudioPlan.promptFor(w2l, QuizLang.AR))
        assertEquals(listOf<AudioStep>(AudioStep.Tts(w2l.stimulus!!.nameAr, QuizLang.AR), AudioStep.Tts(w2l.target.nameAr, QuizLang.AR)), QuizAudioPlan.revealFor(w2l, QuizLang.AR))
    }

    @Test fun oldModesStillUseTheOldPlans() {
        val c = obj("dog", "كلب", "Dog").copy(builtInSoundKey = "dog")
        val q = QuizQuestion(c, listOf(c, obj("cat", "قطة", "Cat")), QuizMode.HEAR_SOUND)
        assertEquals(QuizAudioPlan.prompt(c, QuizLang.AR, QuizMode.HEAR_SOUND), QuizAudioPlan.promptFor(q, QuizLang.AR))
        assertEquals(QuizAudioPlan.reveal(c, QuizLang.AR), QuizAudioPlan.revealFor(q, QuizLang.AR))
        assertEquals(c, q.learnedCard)
    }
}
