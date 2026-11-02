package com.example.familysafetube

import kotlin.random.Random

enum class MatchKind {
    /** A letter tile to the object that starts with it. */
    LETTER_OBJECT,
    /** A digit tile to that many stars. */
    NUMBER_COUNT,
    /** A speaker tile (plays an animal's sound) to the animal's picture. */
    SOUND_PICTURE
}

data class MatchPair(val id: String, val left: QuizCard, val right: QuizCard)

/** One game board: [pairs] plus the order each side is shown in (lists of pair ids). */
data class MatchBoard(val kind: MatchKind, val pairs: List<MatchPair>, val leftOrder: List<String>, val rightOrder: List<String>) {
    fun pair(id: String): MatchPair = pairs.first { it.id == id }
}

object MatchBuilder {
    private const val MIN_PAIRS = 2

    fun pairCount(level: QuizLevel): Int = when (level) { QuizLevel.BABY -> 2; QuizLevel.TODDLER -> 3; QuizLevel.OLDER -> 4 }

    /** A tile that plays [card]'s animal sound when tapped. */
    fun soundTile(card: QuizCard): QuizCard = QuizCard(
        id = "sound_${card.id}", picturePath = "", labelEn = "Sound of ${card.labelEn}", nameAr = card.nameAr, nameEn = card.nameEn,
        enabled = true, needsArabicName = false, builtInSoundKey = card.builtInSoundKey, soundFile = card.soundFile,
        voiceArFile = null, voiceEnFile = null, art = CardArt.Glyph("🔊", 1)
    )

    private fun objectsWithSound(objects: List<QuizCard>) =
        objects.filter { Phonics.isObject(it) && it.hasSound }.distinctBy { it.labelEn.lowercase() }

    /** How many pairs each kind could make with the available cards. */
    fun available(kind: MatchKind, objects: List<QuizCard>, letters: List<QuizCard>, lang: QuizLang): Int = when (kind) {
        MatchKind.LETTER_OBJECT -> {
            val tiles = letters.map { it.id }.toSet()
            objects.filter { Phonics.isObject(it) }.mapNotNull { Phonics.initialKey(it, lang) }
                .filter { Phonics.letterCardId(lang, it) in tiles }.distinct().size
        }
        MatchKind.NUMBER_COUNT -> QuizDecks.COUNTING_MAX
        MatchKind.SOUND_PICTURE -> objectsWithSound(objects).size
    }

    fun possibleKinds(objects: List<QuizCard>, letters: List<QuizCard>, lang: QuizLang): List<MatchKind> =
        MatchKind.values().filter { available(it, objects, letters, lang) >= MIN_PAIRS }

    fun isPlayable(objects: List<QuizCard>, letters: List<QuizCard>, lang: QuizLang): Boolean =
        possibleKinds(objects, letters, lang).isNotEmpty()

    fun build(
        kind: MatchKind,
        objects: List<QuizCard>,
        letters: List<QuizCard>,
        numberTiles: List<QuizCard>,
        countTiles: List<QuizCard>,
        lang: QuizLang,
        pairCount: Int,
        random: Random = Random.Default
    ): MatchBoard? {
        val wanted = pairCount.coerceIn(MIN_PAIRS, 4)
        val pairs: List<MatchPair> = when (kind) {
            MatchKind.LETTER_OBJECT -> {
                val tiles = letters.associateBy { it.id }
                objects.filter { Phonics.isObject(it) }.distinctBy { it.labelEn.lowercase() }
                    .groupBy { Phonics.initialKey(it, lang) }
                    .filterKeys { it != null && Phonics.letterCardId(lang, it) in tiles }
                    .entries.shuffled(random).take(wanted)
                    .map { (key, group) -> MatchPair("p_$key", tiles.getValue(Phonics.letterCardId(lang, key!!)), group[random.nextInt(group.size)]) }
            }
            MatchKind.NUMBER_COUNT -> {
                val digits = numberTiles.associateBy { it.id.substringAfter('_') }
                val dots = countTiles.associateBy { it.id.substringAfter('_') }
                digits.keys.filter { it in dots }.shuffled(random).take(wanted).map { MatchPair("p_$it", digits.getValue(it), dots.getValue(it)) }
            }
            MatchKind.SOUND_PICTURE ->
                objectsWithSound(objects).shuffled(random).take(wanted).map { MatchPair("p_${it.id}", soundTile(it), it) }
        }
        if (pairs.size < MIN_PAIRS) return null
        val ids = pairs.map { it.id }
        return MatchBoard(kind, pairs, ids.shuffled(random), ids.shuffled(random))
    }
}

enum class MatchSide { LEFT, RIGHT }
enum class MatchEvent { SELECTED, MATCHED, WRONG, DONE, IGNORED }

/**
 * The rules of the matching game. Tap one tile, then tap its partner on the other side: a match
 * clears both; a miss just clears the selection (nothing is lost, nobody is scolded).
 */
data class MatchState(
    val board: MatchBoard,
    val solved: Set<String> = emptySet(),
    val selected: Pair<MatchSide, String>? = null,
    val mistakes: Int = 0
) {
    val isDone: Boolean get() = solved.size == board.pairs.size

    fun tap(side: MatchSide, pairId: String): Pair<MatchState, MatchEvent> {
        if (isDone || pairId in solved || board.pairs.none { it.id == pairId }) return this to MatchEvent.IGNORED
        val sel = selected
        if (sel == null || sel.first == side) return copy(selected = side to pairId) to MatchEvent.SELECTED
        return if (sel.second == pairId) {
            val next = copy(solved = solved + pairId, selected = null)
            next to (if (next.isDone) MatchEvent.DONE else MatchEvent.MATCHED)
        } else {
            copy(selected = null, mistakes = mistakes + 1) to MatchEvent.WRONG
        }
    }
}
