package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCategoriesTest {
    // ── ids ──
    @Test fun slug_isLowerCase_andKeepsLettersOfAnyLanguage() {
        assertEquals("birds", ImageCategories.slug("Birds"))
        assertEquals("birds", ImageCategories.slug("  BIRDS  "))
        assertEquals("طيور", ImageCategories.slug(" طيور "))
        assertEquals("veg&fruits", ImageCategories.slug("Veg&Fruits"))
    }

    @Test fun slug_neverContainsTheCharactersThatBreakFileNames() {
        val s = ImageCategories.slug("Sea_life / fish: \\ <big>*?")
        for (c in "_/\\:*?\"<>|") assertFalse("'$c' in '$s'", c in s)
        assertEquals("sea life fish big", s)
        assertEquals("", ImageCategories.slug("___"))
        assertTrue(ImageCategories.slug("x".repeat(200)).length <= 40)
    }

    @Test fun reservedIds_areTheBuiltInOnesAndTheNonPictureGroups() {
        assertEquals(setOf("animals", "letters", "veg&fruits", "other", "videos", "config"), ImageCategories.RESERVED)
        assertEquals(listOf("animals", "letters", "veg&fruits", "other"), ImageCategories.BUILT_IN)
    }

    @Test fun quizAndMatching_useEveryCategoryExceptOther() {
        assertTrue(ImageCategories.isQuizCategory("animals")); assertTrue(ImageCategories.isQuizCategory("birds"))
        assertFalse(ImageCategories.isQuizCategory("other"))
    }

    // ── order ──
    @Test fun order_isAlphabetical_withOtherLast() {
        val names = mapOf("animals" to "Animals", "letters" to "Letters", "veg&fruits" to "Veg & fruits", "other" to "Other", "birds" to "Birds", "cars" to "cars")
        assertEquals(
            listOf("animals", "birds", "cars", "letters", "veg&fruits", "other"),
            ImageCategories.order(names.keys.shuffled()) { names.getValue(it) }
        )
    }

    @Test fun order_otherStaysLast_whateverItsNameIsCalled() {
        val names = mapOf("other" to "Aaa other", "birds" to "Zebra")
        assertEquals(listOf("birds", "other"), ImageCategories.order(listOf("other", "birds")) { names.getValue(it) })
    }

    // ── the book: new categories and removed folders ──
    private val none = emptySet<String>()
    private val all4 = ImageCategories.BUILT_IN.toSet()

    @Test fun aNewFolder_isRemembered_andListedAfterTheBuiltInOnes() {
        val b = CategoryBook().afterScan(mapOf("birds" to "Birds"), all4, none)
        assertEquals(mapOf("birds" to "Birds"), b.known)
        assertEquals(ImageCategories.BUILT_IN + "birds", b.allIds())
        assertTrue(b.pending.isEmpty())
    }

    @Test fun aRemovedFolderWithPicturesOnThePhone_isPending_notDeleted() {
        val seen = CategoryBook().afterScan(mapOf("birds" to "Birds"), all4, setOf("birds"))
        val gone = seen.afterScan(emptyMap(), all4, setOf("birds"))
        assertEquals(setOf("birds"), gone.pending)
        assertEquals("Birds", gone.known["birds"])              // still listed (its pictures are on the phone)
    }

    @Test fun aRemovedFolderWithNoPicturesLeft_justDisappears() {
        val seen = CategoryBook().afterScan(mapOf("birds" to "Birds"), all4, none)
        val gone = seen.afterScan(emptyMap(), all4, none)
        assertTrue(gone.known.isEmpty()); assertTrue(gone.pending.isEmpty())
    }

    @Test fun aRemovedBuiltInFolder_isAskedAboutToo_butNotWhenThePhoneHasNoPicturesOfIt() {
        val b = CategoryBook().afterScan(emptyMap(), all4 - "letters", setOf("letters", "animals"))
        assertEquals(setOf("letters"), b.pending)
        assertTrue(CategoryBook().afterScan(emptyMap(), all4 - "letters", setOf("animals")).pending.isEmpty())
    }

    @Test fun keep_stopsTheQuestion_removeForgetsTheCategory() {
        val gone = CategoryBook(mapOf("birds" to "Birds"), pending = setOf("birds"))
        val kept = gone.resolved("birds", removeFromPhone = false)
        assertTrue(kept.pending.isEmpty()); assertEquals(setOf("birds"), kept.kept); assertEquals("Birds", kept.known["birds"])
        assertTrue(kept.afterScan(emptyMap(), all4, setOf("birds")).pending.isEmpty())          // not asked again
        val removed = gone.resolved("birds", removeFromPhone = true)
        assertTrue(removed.known.isEmpty() && removed.pending.isEmpty() && removed.kept.isEmpty())
    }

    @Test fun aKeptFolderThatComesBack_isSyncedAgain() {
        val kept = CategoryBook(mapOf("birds" to "Birds"), kept = setOf("birds"))
        val back = kept.afterScan(mapOf("birds" to "Birds"), all4, setOf("birds"))
        assertTrue(back.kept.isEmpty() && back.pending.isEmpty())
    }

    @Test fun aRenamedFolder_takesTheNewName() {
        val b = CategoryBook(mapOf("birds" to "birds")).afterScan(mapOf("birds" to "Birds!"), all4, setOf("birds"))
        assertEquals("Birds!", b.known["birds"])
    }

    // ── storing ──
    @Test fun encodeDecode_roundTrips_includingArabicNames() {
        val b = CategoryBook(mapOf("طيور" to "طيور", "cars" to "Cars"), setOf("cars"), setOf("طيور"))
        assertEquals(b, CategoryBook.decode(b.encode()))
    }

    @Test fun decode_survivesGarbage_andRefusesBadIds() {
        assertEquals(CategoryBook(), CategoryBook.decode(null)); assertEquals(CategoryBook(), CategoryBook.decode(""))
        assertEquals(CategoryBook(), CategoryBook.decode("not json {")); assertEquals(CategoryBook(), CategoryBook.decode("[1,2]"))
        val tricky = CategoryBook.decode("""{"known":{"videos":"x","animals":"y","a_b":"z","ok":"Ok"},"pending":["ok",5],"kept":null}""")
        assertEquals(mapOf("ok" to "Ok"), tricky.known)          // reserved ids and ids with "_" are dropped
        assertEquals(setOf("ok"), tricky.pending)
    }
}
