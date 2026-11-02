package com.example.familysafetube

/** Built-in card sets that need no picture files. Each is switched on separately in Settings. */
enum class DeckId(val key: String) {
    AR_LETTERS("arletters"),
    EN_LETTERS("enletters"),
    NUMBERS("numbers"),
    COUNTING("counting");

    companion object {
        fun fromKey(key: String): DeckId? = values().firstOrNull { it.key == key }
    }
}

/**
 * Generates the letter and number cards. Ids are "<deck>_<name>" (e.g. "numbers_3"), so each deck is
 * its own category and distractors come from the same deck. [QuizCard.labelEn] is a UNIQUE description
 * (used to avoid two identical cards in one question); [QuizCard.nameEn] / [QuizCard.nameAr] are what is spoken.
 */
object QuizDecks {
    private class L(val glyph: String, val ar: String, val en: String, val key: String)

    // The 28 Arabic letters: glyph, spoken name, transliteration (unique), id key
    private val ARABIC = listOf(
        L("أ", "ألف", "Alif", "alif"), L("ب", "باء", "Baa", "baa"), L("ت", "تاء", "Taa", "taa"),
        L("ث", "ثاء", "Thaa", "thaa"), L("ج", "جيم", "Jeem", "jeem"), L("ح", "حاء", "Haa", "haa"),
        L("خ", "خاء", "Khaa", "khaa"), L("د", "دال", "Dal", "dal"), L("ذ", "ذال", "Thal", "thal"),
        L("ر", "راء", "Raa", "raa"), L("ز", "زاي", "Zay", "zay"), L("س", "سين", "Seen", "seen"),
        L("ش", "شين", "Sheen", "sheen"), L("ص", "صاد", "Sad", "sad"), L("ض", "ضاد", "Dad", "dad"),
        L("ط", "طاء", "Tah", "tah"), L("ظ", "ظاء", "Zah", "zah"), L("ع", "عين", "Ayn", "ayn"),
        L("غ", "غين", "Ghayn", "ghayn"), L("ف", "فاء", "Faa", "faa"), L("ق", "قاف", "Qaf", "qaf"),
        L("ك", "كاف", "Kaf", "kaf"), L("ل", "لام", "Lam", "lam"), L("م", "ميم", "Meem", "meem"),
        L("ن", "نون", "Noon", "noon"), L("ه", "هاء", "Hah", "hah"), L("و", "واو", "Waw", "waw"),
        L("ي", "ياء", "Yaa", "yaa")
    )

    // English letters with the Arabic way of saying them (used when the quiz speaks Arabic)
    private val ENGLISH_AR = listOf(
        "أي", "بي", "سي", "دي", "إي", "إف", "جي", "إتش", "آي", "جاي", "كاي", "إل", "إم",
        "إن", "أو", "بي", "كيو", "آر", "إس", "تي", "يو", "في", "دبليو", "إكس", "واي", "زد"
    )

    private val NUMBER_AR = listOf("واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة")
    private val NUMBER_EN = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

    const val COUNTING_MAX = 5
    const val COLOR_COUNT = 6          // tile colours the UI cycles through

    private fun card(id: String, label: String, ar: String, en: String, art: CardArt) = QuizCard(
        id = id, picturePath = "", labelEn = label, nameAr = ar, nameEn = en, enabled = true,
        needsArabicName = false, builtInSoundKey = null, soundFile = null, voiceArFile = null, voiceEnFile = null,
        art = art
    )

    fun parse(text: String?): Set<DeckId> =
        text.orEmpty().split(',').mapNotNull { DeckId.fromKey(it.trim()) }.toSet()

    fun format(decks: Set<DeckId>): String = decks.sortedBy { it.ordinal }.joinToString(",") { it.key }

    fun size(deck: DeckId): Int = when (deck) {
        DeckId.AR_LETTERS -> ARABIC.size
        DeckId.EN_LETTERS -> ENGLISH_AR.size
        DeckId.NUMBERS -> NUMBER_AR.size
        DeckId.COUNTING -> COUNTING_MAX
    }

    /** The default cards of one deck (no overrides applied). */
    fun deck(deck: DeckId): List<QuizCard> = when (deck) {
        DeckId.AR_LETTERS -> ARABIC.mapIndexed { i, l ->
            card("arletters_${l.key}", "Arabic letter ${l.en}", l.ar, l.en, CardArt.Glyph(l.glyph, i % COLOR_COUNT))
        }
        DeckId.EN_LETTERS -> ENGLISH_AR.mapIndexed { i, ar ->
            val letter = ('A' + i).toString()
            card("enletters_${letter.lowercase()}", "Letter $letter", ar, letter, CardArt.Glyph(letter, i % COLOR_COUNT))
        }
        DeckId.NUMBERS -> NUMBER_AR.indices.map { i ->
            card("numbers_${i + 1}", "Number ${i + 1}", NUMBER_AR[i], NUMBER_EN[i], CardArt.Glyph((i + 1).toString(), i % COLOR_COUNT))
        }
        DeckId.COUNTING -> (1..COUNTING_MAX).map { n ->
            card("counting_$n", "Count $n", NUMBER_AR[n - 1], NUMBER_EN[n - 1], CardArt.Dots(n, (n - 1) % COLOR_COUNT))
        }
    }

    /** Cards of every enabled deck, with the parent's overrides (voices, names, on/off, focus) applied. */
    fun cards(enabled: Set<DeckId>, overrides: Map<String, CardOverride>, today: Long = QuizCards.todayEpochDay()): List<QuizCard> =
        enabled.sortedBy { it.ordinal }.flatMap { deck(it) }.map { QuizCards.applyOverride(it, overrides[it.id], today) }
}
