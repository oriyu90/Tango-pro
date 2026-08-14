package com.example.domain

import java.text.Normalizer
import java.util.Locale

object AnswerNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)

    fun matches(userAnswer: String, correctAnswer: String): Boolean =
        normalize(userAnswer) == normalize(correctAnswer)
}
