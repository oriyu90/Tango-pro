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
        BundledGroupSpec("basic_chinese", "basic_chinese_words.csv", "中国語基本単語", "zh", true),
        BundledGroupSpec("v2_lv1_intro_words", "lv1_intro_words.csv", "Lv.1中学入門英単語", "en"),
        BundledGroupSpec("v2_lv1_intro_phrases", "lv1_intro_phrases.csv", "Lv.1中学入門英熟語", "en"),
        BundledGroupSpec("v2_lv2_junior_beginner_words", "lv2_junior_beginner_words.csv", "Lv.2中学初級英単語", "en"),
        BundledGroupSpec("v2_lv2_junior_beginner_phrases", "lv2_junior_beginner_phrases.csv", "Lv.2中学初級英熟語", "en"),
        BundledGroupSpec("v2_lv3_junior_intermediate_words", "lv3_junior_intermediate_words.csv", "Lv.3中学中級英単語", "en"),
        BundledGroupSpec("v2_lv3_junior_intermediate_phrases", "lv3_junior_intermediate_phrases.csv", "Lv.3中学中級英熟語", "en"),
        BundledGroupSpec("v2_lv4_high_intro_words", "lv4_high_intro_words.csv", "Lv.4高校入門英単語", "en"),
        BundledGroupSpec("v2_lv4_high_intro_phrases", "lv4_high_intro_phrases.csv", "Lv.4高校入門英熟語", "en"),
        BundledGroupSpec("v2_lv5_high_beginner_words", "lv5_high_beginner_words.csv", "Lv.5高校初級英単語", "en"),
        BundledGroupSpec("v2_lv5_high_beginner_phrases", "lv5_high_beginner_phrases.csv", "Lv.5高校初級英熟語", "en"),
        BundledGroupSpec("v2_lv6_high_intermediate_words", "lv6_high_intermediate_words.csv", "Lv.6高校中級英単語", "en"),
        BundledGroupSpec("v2_lv6_high_intermediate_phrases", "lv6_high_intermediate_phrases.csv", "Lv.6高校中級英熟語", "en"),
        BundledGroupSpec("v2_lv7_high_advanced_words", "lv7_high_advanced_words.csv", "Lv.7高校上級英単語", "en"),
        BundledGroupSpec("v2_lv7_high_advanced_phrases", "lv7_high_advanced_phrases.csv", "Lv.7高校上級英熟語", "en"),
        BundledGroupSpec("v2_lv8_expert_words", "lv8_expert_words.csv", "Lv.8発展英単語", "en"),
        BundledGroupSpec("v2_lv8_expert_phrases", "lv8_expert_phrases.csv", "Lv.8発展英熟語", "en")
    )
}
