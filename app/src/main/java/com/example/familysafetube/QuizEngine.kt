package com.example.familysafetube

import kotlin.random.Random

data class CardStats(val shown: Int = 0, val correct: Int = 0, val wrong: Int = 0) {
    fun record(correct: Boolean) = copy(
        shown = shown + 1,
        correct = this.correct + if (correct) 1 else 0,
        wrong = wrong + if (correct) 0 else 1
    )
}

data class QuizQuestion(
    /** The correct answer (always one of [options]). */
    val target: QuizCard,
    val options: List<QuizCard>,
    val mode: QuizMode = QuizMode.HEAR_NAME,
    /** LETTER_TO_WORD: the letter being asked about (shown and spoken; the answer is an object that starts with it). */
    val promptCard: QuizCard? = null,
    /** WORD_TO_LETTER: the object shown and spoken (the answer is the letter it starts with). */
    val stimulus: QuizCard? = null
) {
    /** The card the child "learns" from a correct answer (used for stickers): the object, not the letter. */
    val learnedCard: QuizCard get() = stimulus ?: target
}

/**
 * Picks questions. Pure and deterministic given a [Random], so it is fully unit-tested.
 *
 *  - Only enabled cards, and never two pictures with the same label in one question.
 *  - Distractors come from the same category (animals with animals, fruit with fruit) when possible.
 *  - Words the child gets wrong come back more often; words just asked are skipped.
 *  - After several misses in a row the question gets easier (2 pictures instead of 3).
 */
object QuizEngine {
    const val MAX_OPTIONS = 3
    const val MIN_OPTIONS = 2
    private const val MISSES_BEFORE_EASIER = 3
    const val RECENT_SIZE = 3
    /** A starred "word of the week" is this many times more likely to be asked. */
    const val FOCUS_BOOST = 3.0

    fun playableCards(cards: List<QuizCard>): List<QuizCard> =
        cards.filter { it.enabled }.distinctBy { it.labelEn.lowercase() }

    fun isPlayable(cards: List<QuizCard>): Boolean = playableCards(cards).size >= MIN_OPTIONS

    fun optionCount(consecutiveMisses: Int): Int =
        if (consecutiveMisses >= MISSES_BEFORE_EASIER) MIN_OPTIONS else MAX_OPTIONS

    /** Pictures per question for a level: Baby always 2; others drop to 2 after enough misses in a row. */
    fun optionCount(consecutiveMisses: Int, level: QuizLevel): Int =
        if (consecutiveMisses >= level.missesBeforeEasier) MIN_OPTIONS else level.options.coerceIn(MIN_OPTIONS, MAX_OPTIONS)

    /** Unseen cards weigh 1; each miss adds, each success subtracts a little (never below 0.3). */
    fun weight(stats: CardStats?): Double {
        val s = stats ?: CardStats()
        return (1.0 + 1.5 * s.wrong - 0.3 * s.correct).coerceAtLeast(0.3)
    }

    fun pick(
        cards: List<QuizCard>,
        stats: Map<String, CardStats>,
        recentIds: List<String>,
        optionCount: Int,
        random: Random = Random.Default,
        /** Chance (0..1) that a card WITH a sound is asked as a sound-only question. 0 = never. */
        soundOnlyChance: Double = 0.0
    ): QuizQuestion? {
        val pool = playableCards(cards)
        if (pool.size < MIN_OPTIONS) return null

        val targets = pool.filter { it.id !in recentIds }.ifEmpty { pool }
        val target = weightedPick(targets, stats, random)

        val wanted = optionCount.coerceIn(MIN_OPTIONS, MAX_OPTIONS)
        val sameCategory = pool.filter { it.id != target.id && it.category == target.category }
        val others = if (sameCategory.size >= wanted - 1) sameCategory else pool.filter { it.id != target.id }
        val distractors = others.shuffled(random).take(minOf(wanted, others.size + 1) - 1)
        val mode = if (soundOnlyChance > 0.0 && target.hasSound && random.nextDouble() < soundOnlyChance) QuizMode.HEAR_SOUND
                   else QuizMode.HEAR_NAME
        return QuizQuestion(target, (distractors + target).shuffled(random), mode)
    }

    private fun weightedPick(cards: List<QuizCard>, stats: Map<String, CardStats>, random: Random): QuizCard {
        val weights = cards.map { weight(stats[it.id]) * (if (it.focus) FOCUS_BOOST else 1.0) }
        var r = random.nextDouble() * weights.sum()
        for (i in cards.indices) {
            r -= weights[i]
            if (r <= 0) return cards[i]
        }
        return cards.last()
    }
}
