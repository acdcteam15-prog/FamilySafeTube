package com.example.familysafetube

enum class WordLevel { NEW, PRACTICING, LEARNED, STRUGGLING }

data class WordProgress(val card: QuizCard, val stats: CardStats, val level: WordLevel)

data class ProgressSummary(
    val learned: List<WordProgress>,
    val struggling: List<WordProgress>,
    val practicing: List<WordProgress>,
    val newCount: Int,
    val totalAnswers: Int,
    /** Overall accuracy 0..100, or null if nothing has been answered yet. */
    val accuracyPercent: Int?
)

/**
 * Turns the per-card counters into something a parent can read: which words the child knows and which
 * need more practice. Pure and unit-tested; the thresholds are deliberately forgiving (a baby taps by chance).
 */
object QuizProgress {
    private const val MIN_ANSWERS = 3
    private const val LEARNED_ACCURACY = 0.75
    private const val STRUGGLING_ACCURACY = 0.5
    private const val MIN_CORRECT_TO_LEARN = 3

    fun level(stats: CardStats?): WordLevel {
        if (stats == null || stats.shown == 0) return WordLevel.NEW
        val accuracy = stats.correct.toDouble() / stats.shown
        return when {
            stats.shown >= MIN_ANSWERS && stats.correct >= MIN_CORRECT_TO_LEARN && accuracy >= LEARNED_ACCURACY -> WordLevel.LEARNED
            stats.shown >= MIN_ANSWERS && accuracy < STRUGGLING_ACCURACY -> WordLevel.STRUGGLING
            else -> WordLevel.PRACTICING
        }
    }

    /** Only cards currently switched on in the quiz are counted. */
    fun summarize(cards: List<QuizCard>, stats: Map<String, CardStats>): ProgressSummary {
        val words = cards.filter { it.enabled }.map {
            val s = stats[it.id] ?: CardStats()
            WordProgress(it, s, level(s))
        }
        val answered = words.sumOf { it.stats.shown }
        val correct = words.sumOf { it.stats.correct }
        return ProgressSummary(
            learned = words.filter { it.level == WordLevel.LEARNED }.sortedByDescending { it.stats.correct },
            struggling = words.filter { it.level == WordLevel.STRUGGLING }.sortedByDescending { it.stats.wrong },
            practicing = words.filter { it.level == WordLevel.PRACTICING }.sortedByDescending { it.stats.shown },
            newCount = words.count { it.level == WordLevel.NEW },
            totalAnswers = answered,
            accuracyPercent = if (answered == 0) null else Math.round(100.0 * correct / answered).toInt()
        )
    }
}

/**
 * Counts correct answers towards a round of [size] (a round only triggers a celebration and a calm
 * pause; it never gives anything extra). Wrong answers don't reset progress: nothing is punished.
 */
class RoundTracker(private val size: Int = QuizRules.SET_SIZE) {
    var correctInRound: Int = 0
        private set
    var roundsCompleted: Int = 0
        private set

    /** Returns true when this answer completes the round (the counter then starts again at 0). */
    fun correctAnswer(): Boolean {
        correctInRound++
        if (correctInRound < size) return false
        correctInRound = 0
        roundsCompleted++
        return true
    }
}
