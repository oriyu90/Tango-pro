package com.example.domain

import java.util.Locale

data class StudyLanguage(
    val code: String,
    val displayName: String,
    val shortName: String,
    val locale: Locale?
) {
    companion object {
        val supported = listOf(
            StudyLanguage("en", "英語", "英", Locale.forLanguageTag("en-US")),
            StudyLanguage("zh", "中国語", "中", Locale.forLanguageTag("zh-CN")),
            StudyLanguage("fr", "フランス語", "仏", Locale.forLanguageTag("fr-FR")),
            StudyLanguage("pt", "ポルトガル語", "葡", Locale.forLanguageTag("pt-BR")),
            StudyLanguage("none", "読み上げなし", "外", null)
        )

        val supportedCodes: Set<String> = supported.mapTo(linkedSetOf()) { it.code }

        fun fromCode(code: String?): StudyLanguage =
            supported.firstOrNull { it.code == code } ?: supported.first()

        fun normalize(code: String?): String = fromCode(code).code
    }
}
