package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizRulesTest {
    @Test fun label_fromFileName() {
        assertEquals("Lion", QuizRules.labelFromFileName("animals_lion.png"))
        assertEquals("Polar bear", QuizRules.labelFromFileName("animals_polar_bear.jpg"))
        assertEquals("Apple", QuizRules.labelFromFileName("veg&fruits_apple.webp"))
    }
}
