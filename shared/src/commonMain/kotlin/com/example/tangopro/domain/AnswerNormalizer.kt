package com.example.tangopro.domain

expect fun normalizeNfkc(value: String): String

object AnswerNormalizer {
    fun normalize(value: String): String = normalizeNfkc(value.trim()).lowercase()
    fun matches(userAnswer: String, correctAnswer: String): Boolean =
        normalize(userAnswer) == normalize(correctAnswer)
}
