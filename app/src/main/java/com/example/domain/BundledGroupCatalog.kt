package com.example.domain

data class BundledGroupSpec(
    val id: String,
    val assetFileName: String,
    val displayName: String,
    val language: String,
    val includedInLegacyBootstrap: Boolean = false
) {
    val preferenceKey: String = "bundled_group_imported_$id"
}

object BundledGroupCatalog {
    val all: List<BundledGroupSpec> = listOf(
        BundledGroupSpec("basic_phrases", "basic_english_phrases.csv", "英語基本フレーズ", "en", true),
        BundledGroupSpec("basic_words", "basic_english_words.csv", "英語基本単語", "en", true),
        BundledGroupSpec("basic_chinese", "basic_chinese_words.csv", "中国語基本単語", "zh", true),
        BundledGroupSpec("common_test_words", "common_test_words.csv", "共テ用英単語", "en"),
        BundledGroupSpec("common_test_phrases", "common_test_phrases.csv", "共テ用英熟語", "en"),
        BundledGroupSpec("advanced_words", "advanced_words.csv", "難関大用英単語", "en"),
        BundledGroupSpec("advanced_phrases", "advanced_phrases.csv", "難関大用英熟語", "en"),
        BundledGroupSpec("pre_high_school", "pre_high_school_vocab.csv", "高校レベル未満の単熟語", "en")
    )
}
