package com.example.familysafetube

/** How hard the quiz is. Chosen by the parent in Settings. */
enum class QuizLevel(
    val code: String,
    /** Pictures per question. */
    val options: Int,
    /** Chance that a card with a sound is asked as a sound-only question. */
    val soundOnlyChance: Double,
    /** Seconds of no touching before the question repeats by itself. */
    val idleRepeatMs: Long,
    /** Wrong answers in a row before the question drops to 2 pictures. */
    val missesBeforeEasier: Int
) {
    BABY("baby", 2, 0.0, 14_000L, Int.MAX_VALUE),
    TODDLER("toddler", 3, 0.3, 10_000L, 3),
    OLDER("older", 3, 0.5, 8_000L, 4);

    companion object {
        fun fromCode(code: String?): QuizLevel = values().firstOrNull { it.code == code } ?: TODDLER
    }
}

/** Suggests a level change from the child's results (shown to the parent on the dashboard; never automatic). */
object LevelAdvisor {
    private const val MIN_ANSWERS_TO_ADVISE = 20
    private const val MIN_ANSWERS_TO_EASE = 15
    private const val GOOD_ACCURACY = 85
    private const val POOR_ACCURACY = 40
    private const val MIN_LEARNED_TO_ADVANCE = 5

    /** The suggested level, or null to stay. */
    fun suggest(current: QuizLevel, summary: ProgressSummary): QuizLevel? {
        val accuracy = summary.accuracyPercent ?: return null
        val levels = QuizLevel.values()
        if (summary.totalAnswers >= MIN_ANSWERS_TO_ADVISE && accuracy >= GOOD_ACCURACY &&
            summary.learned.size >= MIN_LEARNED_TO_ADVANCE && current.ordinal < levels.lastIndex
        ) return levels[current.ordinal + 1]
        if (summary.totalAnswers >= MIN_ANSWERS_TO_EASE && accuracy < POOR_ACCURACY && current.ordinal > 0)
            return levels[current.ordinal - 1]
        return null
    }
}
