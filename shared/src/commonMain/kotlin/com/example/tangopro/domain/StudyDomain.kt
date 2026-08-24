package com.example.tangopro.domain

import com.example.tangopro.model.StudyQuestion
import com.example.tangopro.model.WordRecord
import kotlin.random.Random

object StudyProgress {
    fun isLearned(word: WordRecord): Boolean = word.studyCount >= 2 && word.isCorrectLast
    fun isVague(word: WordRecord): Boolean = word.studyCount == 1 && word.isCorrectLast
    fun needsReview(word: WordRecord): Boolean = !isLearned(word) && !isVague(word)
}

object StudyRound {
    fun current(words: List<WordRecord>): Int =
        if (words.isEmpty()) 1 else words.minOf { it.studyCount.coerceAtLeast(0) } + 1

    fun label(round: Int): String = "${round.coerceAtLeast(1)}周目"
    fun visualTier(round: Int): Int = round.takeIf { it in 1..4 } ?: 0
}

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
        Option(LEARNED_RANDOM, "学習済をランダム"),
    )
    private val supportedIds = options.mapTo(hashSetOf()) { it.id }

    fun normalize(id: String?): String = id?.takeIf(supportedIds::contains) ?: RECOMMEND

    fun matching(words: List<WordRecord>, mode: String): List<WordRecord> = when (normalize(mode)) {
        RECOMMEND -> words
        UNSTUDIED -> words.filter { it.studyCount == 0 }
        WEAK -> words.filter { StudyProgress.isVague(it) || (it.studyCount > 0 && !it.isCorrectLast) }
        VAGUE_RANDOM -> words.filter(StudyProgress::isVague)
        LEARNED_RANDOM -> words.filter(StudyProgress::isLearned)
        else -> error("normalize() returned an unsupported study filter")
    }

    fun select(words: List<WordRecord>, mode: String, random: Random = Random.Default): List<WordRecord> {
        val candidates = matching(words, mode)
        return when (normalize(mode)) {
            RECOMMEND -> buildList(candidates.size) {
                addAll(candidates.filter(StudyProgress::needsReview).shuffled(random))
                addAll(candidates.filter(StudyProgress::isVague).shuffled(random))
                addAll(candidates.filter(StudyProgress::isLearned).shuffled(random))
            }
            else -> candidates.shuffled(random)
        }
    }
}

data class StudySettings(
    val directionForward: Boolean = true,
    val multipleChoice: Boolean = true,
    val filterMode: String = StudyFilterMode.RECOMMEND,
    val selectedTag: String = "すべて",
    val rangeStart: Int = 1,
    val rangeEnd: Int = -1,
    val useRangeConstraint: Boolean = false,
    val quizCount: Int = DEFAULT_QUIZ_COUNT,
) {
    companion object {
        const val QUIZ_COUNT_ALL = 100_000
        const val DEFAULT_QUIZ_COUNT = 10
        val allowedQuizCounts = setOf(5, 10, 20, 50, QUIZ_COUNT_ALL)

        fun normalize(settings: StudySettings): StudySettings = settings.copy(
            directionForward = settings.multipleChoice && settings.directionForward,
            filterMode = StudyFilterMode.normalize(settings.filterMode),
            selectedTag = settings.selectedTag.ifBlank { "すべて" },
            rangeStart = settings.rangeStart.coerceAtLeast(1),
            rangeEnd = settings.rangeEnd.takeIf { it == -1 || it >= 1 } ?: -1,
            quizCount = settings.quizCount.takeIf { it in allowedQuizCounts } ?: DEFAULT_QUIZ_COUNT,
        )
    }
}

object QuizQuestionFactory {
    private val japaneseFallbacks = listOf("りんご", "本", "走る", "犬", "机", "山", "海", "学校", "花", "車")
    private val targetLanguageFallbacks = listOf("apple", "book", "run", "dog", "desk", "mountain", "sea", "school", "flower", "car")

    fun create(
        sessionWords: List<WordRecord>,
        allWords: List<WordRecord>,
        directionForward: Boolean,
        isMultipleChoice: Boolean,
        random: Random = Random.Default,
    ): List<StudyQuestion> = sessionWords.map { word ->
        val questionText = if (directionForward) word.term else word.meaning
        val correctAnswer = if (directionForward) word.meaning else word.term
        val choices = if (isMultipleChoice) {
            buildChoices(word, correctAnswer, allWords, directionForward, random)
        } else emptyList()
        StudyQuestion(word, questionText, correctAnswer, choices, directionForward, isMultipleChoice)
    }

    private fun buildChoices(
        word: WordRecord,
        correctAnswer: String,
        allWords: List<WordRecord>,
        directionForward: Boolean,
        random: Random,
    ): List<String> {
        val choices = allWords.asSequence()
            .filter { it.id != word.id }
            .map { if (directionForward) it.meaning else it.term }
            .filter { it != correctAnswer }
            .distinct()
            .toList()
            .shuffled(random)
            .take(3)
            .toMutableList()
        val fallbacks = if (directionForward) japaneseFallbacks else targetLanguageFallbacks
        choices += fallbacks.filter { it != correctAnswer && it !in choices }
            .shuffled(random)
            .take(3 - choices.size)
        choices += correctAnswer
        return choices.distinct().shuffled(random)
    }
}

data class StudyLanguage(
    val code: String,
    val displayName: String,
    val shortName: String,
    val speechTag: String?,
) {
    companion object {
        val supported = listOf(
            StudyLanguage("en", "英語", "英", "en-US"),
            StudyLanguage("zh", "中国語", "中", "zh-CN"),
            StudyLanguage("fr", "フランス語", "仏", "fr-FR"),
            StudyLanguage("pt", "ポルトガル語", "葡", "pt-BR"),
            StudyLanguage("none", "読み上げなし", "外", null),
        )
        val supportedCodes = supported.mapTo(linkedSetOf()) { it.code }
        fun fromCode(code: String?): StudyLanguage = supported.firstOrNull { it.code == code } ?: supported.first()
        fun normalize(code: String?): String = fromCode(code).code
    }
}
