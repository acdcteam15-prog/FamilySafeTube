package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizStickersTest {
    private fun card(id: String) = QuizCard(id, "/p", id, id, id, true, false, null, null, null, null)

    @Test fun award_firstNewCorrectAnswerInOrder() {
        val eligible = setOf("a", "b", "c", "d")
        assertEquals("b", StickerAlbum.award(listOf("a", "b", "c"), collected = listOf("a"), eligible = eligible))
        assertEquals("a", StickerAlbum.award(listOf("a", "b"), emptyList(), eligible))
    }

    @Test fun award_noneWhenEverythingIsCollectedOrIneligible() {
        assertNull(StickerAlbum.award(listOf("a", "b"), listOf("a", "b"), setOf("a", "b")))
        assertNull(StickerAlbum.award(listOf("x"), emptyList(), setOf("a")))          // not a real card (e.g. a deleted one)
        assertNull(StickerAlbum.award(emptyList(), emptyList(), setOf("a")))
    }

    @Test fun award_ignoresRepeats() {
        assertEquals("a", StickerAlbum.award(listOf("a", "a", "a"), emptyList(), setOf("a")))
    }

    @Test fun store_roundTrip_andTolerance() {
        assertEquals(listOf("a", "b"), StickerAlbum.decode(StickerAlbum.encode(listOf("a", "b", "a"))))
        assertTrue(StickerAlbum.decode(null).isEmpty()); assertTrue(StickerAlbum.decode("{bad").isEmpty()); assertTrue(StickerAlbum.decode("{}").isEmpty())
        assertEquals(listOf("a"), StickerAlbum.decode("""["a", 5, "", null, "a"]"""))
    }

    @Test fun progress_countsOnlyExistingCards() {
        val p = StickerAlbum.progress(listOf("a", "gone", "b"), setOf("a", "b", "c"))
        assertEquals(StickerAlbum.Progress(2, 3), p)
    }

    @Test fun albumOrder_newestFirst_thenTheMissing() {
        val cards = listOf(card("a"), card("b"), card("c"), card("d"))
        val order = StickerAlbum.albumOrder(cards, collected = listOf("a", "c"))
        assertEquals(listOf("c" to true, "a" to true, "b" to false, "d" to false), order.map { it.first.id to it.second })
    }

    @Test fun albumOrder_skipsCollectedIdsThatNoLongerExist() {
        val order = StickerAlbum.albumOrder(listOf(card("a"), card("b")), collected = listOf("gone", "a"))
        assertEquals(listOf("a" to true, "b" to false), order.map { it.first.id to it.second })
        assertFalse(order.any { it.first.id == "gone" })
    }
}
