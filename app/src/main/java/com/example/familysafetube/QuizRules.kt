package com.example.familysafetube

/** Small shared quiz helpers. (Question building lives in [QuizEngine].) */
object QuizRules {
    /** Correct answers that complete one "round" (a celebration, nothing else; there are no rewards). */
    const val SET_SIZE = 5

    /** "animals_polar_bear.png" -> "Polar bear" (same naming rule the image viewer uses). */
    fun labelFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.')
            .substringAfter('_')
            .replace('_', ' ')
            .trim()
            .replaceFirstChar { it.uppercase() }
}
