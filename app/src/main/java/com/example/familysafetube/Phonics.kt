package com.example.familysafetube

import kotlin.random.Random

/** First-letter knowledge for the letter <-> word questions. Pure and unit-tested. */
object Phonics {
    private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")      // vowel marks (tashkeel) and tatweel

    /** Arabic character -> the id key of its letter in the Arabic-letters deck ("meem", "alif"...). */
    private val AR_KEYS: Map<Char, String> = buildMap {
        for (c in "اأإآٱ") put(c, "alif")
        put('ب', "baa"); put('ت', "taa"); put('ث', "thaa"); put('ج', "jeem"); put('ح', "haa"); put('خ', "khaa")
        put('د', "dal"); put('ذ', "thal"); put('ر', "raa"); put('ز', "zay"); put('س', "seen"); put('ش', "sheen")
        put('ص', "sad"); put('ض', "dad"); put('ط', "tah"); put('ظ', "zah"); put('ع', "ayn"); put('غ', "ghayn")
        put('ف', "faa"); put('ق', "qaf"); put('ك', "kaf"); put('ل', "lam"); put('م', "meem"); put('ن', "noon")
        put('ه', "hah"); put('و', "waw"); put('ي', "yaa"); put('ى', "yaa")
    }

    fun stripMarks(s: String): String = MARKS.replace(s, "").trim()

    /** "مَوزة" -> "meem"; skips the article "ال" ("الفيل" -> "faa"). Null if it doesn't start with an Arabic letter. */
    fun arabicInitialKey(word: String): String? {
        var w = stripMarks(word)
        if (w.length > 3 && w.startsWith("ال")) w = w.drop(2)
        return w.firstOrNull()?.let { AR_KEYS[it] }
    }

    /** "Banana" -> "b". Null if it doesn't start with a Latin letter. */
    fun englishInitialKey(word: String): String? =
        word.trim().firstOrNull()?.takeIf { it in 'A'..'Z' || it in 'a'..'z' }?.lowercaseChar()?.toString()

    /** Initial-letter key of a card for the quiz language. */
    fun initialKey(card: QuizCard, lang: QuizLang): String? =
        if (lang == QuizLang.AR) arabicInitialKey(card.nameAr) else englishInitialKey(card.nameEn)

    /** Id of the deck card showing that letter. */
    fun letterCardId(lang: QuizLang, key: String): String = (if (lang == QuizLang.AR) "arletters_" else "enletters_") + key

    /** Picture / family cards (not the generated letter and number tiles): the things a letter can be matched to. */
    fun isObject(card: QuizCard): Boolean = card.enabled && card.art is CardArt.Picture
}

/**
 * Builds "letter <-> word" questions:
 *  - LETTER_TO_WORD: hear "ميم", pick the picture of something that starts with م.
 *  - WORD_TO_LETTER: see and hear "موزة", pick the letter م.
 */
object PhonicsEngine {
    private const val MIN_OPTIONS = 2

    fun isPossible(objects: List<QuizCard>, letters: List<QuizCard>, lang: QuizLang): Boolean =
        usableGroups(objects, letters, lang).isNotEmpty() && letterIds(letters, lang).size >= MIN_OPTIONS &&
            objects.filter { Phonics.isObject(it) }.mapNotNull { Phonics.initialKey(it, lang) }.distinct().size >= MIN_OPTIONS

    private fun letterIds(letters: List<QuizCard>, lang: QuizLang) =
        letters.filter { it.id.startsWith(if (lang == QuizLang.AR) "arletters_" else "enletters_") }.associateBy { it.id }

    /** letter key -> objects starting with it (only keys whose letter tile exists). */
    private fun usableGroups(objects: List<QuizCard>, letters: List<QuizCard>, lang: QuizLang): Map<String, List<QuizCard>> {
        val tiles = letterIds(letters, lang)
        return objects.filter { Phonics.isObject(it) }
            .distinctBy { it.labelEn.lowercase() }
            .groupBy { Phonics.initialKey(it, lang) }
            .filterKeys { it != null && Phonics.letterCardId(lang, it) in tiles }
            .mapKeys { it.key!! }
    }

    fun pick(
        objects: List<QuizCard>,
        letters: List<QuizCard>,
        lang: QuizLang,
        optionCount: Int,
        recentIds: List<String> = emptyList(),
        random: Random = Random.Default,
        /** Force one kind (tests); null = a coin flip. */
        kind: QuizMode? = null
    ): QuizQuestion? {
        val groups = usableGroups(objects, letters, lang)
        val tiles = letterIds(letters, lang)
        val n = optionCount.coerceIn(MIN_OPTIONS, 3)
        if (groups.isEmpty() || groups.size < MIN_OPTIONS) return null

        val keys = groups.keys.toList()
        val freshKeys = keys.filter { k -> groups.getValue(k).any { it.id !in recentIds } }.ifEmpty { keys }
        val key = freshKeys[random.nextInt(freshKeys.size)]
        val target = groups.getValue(key).let { g -> g.filter { it.id !in recentIds }.ifEmpty { g } }.let { it[random.nextInt(it.size)] }
        val mode = kind ?: if (random.nextBoolean()) QuizMode.LETTER_TO_WORD else QuizMode.WORD_TO_LETTER
        val letter = tiles.getValue(Phonics.letterCardId(lang, key))

        return if (mode == QuizMode.LETTER_TO_WORD) {
            // distractors: objects whose first letter is a DIFFERENT letter (one object per letter, so none can also be right)
            val others = keys.filter { it != key }.shuffled(random).take(n - 1).map { k -> groups.getValue(k).let { it[random.nextInt(it.size)] } }
            QuizQuestion(target, (others + target).shuffled(random), QuizMode.LETTER_TO_WORD, promptCard = letter)
        } else {
            // distractors: other letters (any letter of the deck, so the choice is not just "the ones that have pictures")
            val others = tiles.values.filter { it.id != letter.id }.shuffled(random).take(n - 1)
            QuizQuestion(letter, (others + letter).shuffled(random), QuizMode.WORD_TO_LETTER, stimulus = target)
        }
    }
}
