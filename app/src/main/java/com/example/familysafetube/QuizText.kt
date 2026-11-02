package com.example.familysafetube

/** Small text helpers. */
object QuizText {
    /** True if the text contains any Arabic-script letter. */
    fun containsArabic(text: String): Boolean = text.any {
        val c = it.code
        c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0xFB50..0xFDFF || c in 0xFE70..0xFEFF
    }
}
