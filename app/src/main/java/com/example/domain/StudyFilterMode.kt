package com.example.domain

import com.example.data.Word
import kotlin.random.Random

/** Stable filter identifiers shared by preferences, UI, and session selection. */
object StudyFilterMode {
    const val RECOMMEND = "recommend"
    const val UNSTUDIED = "unstudied"
    const val WEAK = "weak"
    const val VAGUE_RANDOM = "vague_random"
    const val LEARNED_RANDOM = "learned_random"

    data class Option(val id: String, val label: String)

    val options = listOf(
        Option(RECOMMEND, "おすすめ"),
        Option(UNSTUDIED, "未学習のみ"),
        Option(WEAK, "うろ覚え＆ミスのみ"),
        Option(VAGUE_RANDOM, "うろ覚えをランダム"),
        Option(LEARNED_RANDOM, "学習済をランダム")
    )

    private val supportedIds = options.mapTo(hashSetOf()) { it.id }

    /** Removed and unknown preference values safely migrate to the new default. */
    fun normalize(id: String?): String = id?.takeIf(supportedIds::contains) ?: RECOMMEND

    fun matching(words: List<Word>, mode: String): List<Word> = when (normalize(mode)) {
        RECOMMEND -> words
        UNSTUDIED -> words.filter { it.studyCount == 0 }
        WEAK -> words.filter {
            StudyProgress.isVague(it) || (it.studyCount > 0 && !it.isCorrectLast)
        }
        VAGUE_RANDOM -> words.filter(StudyProgress::isVague)
        LEARNED_RANDOM -> words.filter(StudyProgress::isLearned)
        else -> error("normalize() must return a supported study filter")
    }

    fun orderForSession(
        words: List<Word>,
        mode: String,
        random: Random = Random.Default
    ): List<Word> = when (normalize(mode)) {
        RECOMMEND -> buildList(words.size) {
            // Every word remains eligible so a fully learned book can be repeated forever.
            addAll(words.filter(StudyProgress::needsReview).shuffled(random))
            addAll(words.filter(StudyProgress::isVague).shuffled(random))
            addAll(words.filter(StudyProgress::isLearned).shuffled(random))
        }
        UNSTUDIED, WEAK, VAGUE_RANDOM, LEARNED_RANDOM -> words.shuffled(random)
        else -> error("normalize() must return a supported study filter")
    }

    fun select(words: List<Word>, mode: String, random: Random = Random.Default): List<Word> =
        orderForSession(matching(words, mode), mode, random)
}
