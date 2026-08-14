package com.example

import com.example.data.CsvExporter
import com.example.data.CsvParser
import com.example.data.Word
import com.example.domain.AnswerNormalizer
import com.example.domain.QuizQuestionFactory
import com.example.domain.StudyFilterMode
import com.example.domain.StudyLanguage
import com.example.domain.StudyProgress
import com.example.domain.StudySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLogicTest {
    @Test
    fun `French and Portuguese are supported TTS languages`() {
        assertEquals("fr-FR", StudyLanguage.fromCode("fr").locale?.toLanguageTag())
        assertEquals("pt-BR", StudyLanguage.fromCode("pt").locale?.toLanguageTag())
        assertTrue(setOf("en", "zh", "fr", "pt", "none").all { it in StudyLanguage.supportedCodes })
    }

    @Test
    fun `typing study settings normalize to reverse direction`() {
        val normalized = StudySettings.normalize(
            StudySettings(directionForward = true, multipleChoice = false, quizCount = 999)
        )

        assertFalse(normalized.directionForward)
        assertEquals(StudySettings.DEFAULT_QUIZ_COUNT, normalized.quizCount)
    }

    @Test
    fun `typing comparison normalizes case and character width`() {
        assertTrue(AnswerNormalizer.matches(" ＡＰＰＬＥ ", "apple"))
        assertTrue(AnswerNormalizer.matches("English", "english"))
        assertFalse(AnswerNormalizer.matches("apple", "apples"))
    }

    @Test
    fun `wrong first answer is review rather than vague`() {
        val word = word(id = 1, studyCount = 1, isCorrectLast = false)

        assertFalse(StudyProgress.isVague(word))
        assertTrue(StudyProgress.needsReview(word))
    }

    @Test
    fun `removed filters migrate to recommend`() {
        assertEquals(StudyFilterMode.RECOMMEND, StudyFilterMode.normalize(null))
        assertEquals(StudyFilterMode.RECOMMEND, StudyFilterMode.normalize("all"))
        assertEquals(StudyFilterMode.RECOMMEND, StudyFilterMode.normalize("incorrect"))
        assertEquals(StudyFilterMode.RECOMMEND, StudyFilterMode.normalize("learned_once"))
    }

    @Test
    fun `recommend keeps fully learned books repeatable`() {
        val learned = listOf(
            word(id = 1, studyCount = 3, isCorrectLast = true),
            word(id = 2, studyCount = 4, isCorrectLast = true)
        )

        val selected = StudyFilterMode.select(learned, StudyFilterMode.RECOMMEND)

        assertEquals(learned.map { it.id }.toSet(), selected.map { it.id }.toSet())
    }

    @Test
    fun `recommend prioritizes review then vague then learned`() {
        val learned = word(id = 1, studyCount = 2, isCorrectLast = true)
        val vague = word(id = 2, studyCount = 1, isCorrectLast = true)
        val review = word(id = 3, studyCount = 2, isCorrectLast = false)

        assertEquals(
            listOf(review, vague, learned),
            StudyFilterMode.select(listOf(learned, vague, review), StudyFilterMode.RECOMMEND)
        )
    }

    @Test
    fun `vague random includes only one-time correct words`() {
        val vague = word(id = 1, studyCount = 1, isCorrectLast = true)
        val wrong = word(id = 2, studyCount = 1, isCorrectLast = false)
        val learned = word(id = 3, studyCount = 2, isCorrectLast = true)

        assertEquals(
            listOf(vague),
            StudyFilterMode.select(listOf(vague, wrong, learned), StudyFilterMode.VAGUE_RANDOM)
        )
    }

    @Test
    fun `learned random excludes vague and latest mistakes`() {
        val vague = word(id = 1, studyCount = 1, isCorrectLast = true)
        val wrong = word(id = 2, studyCount = 3, isCorrectLast = false)
        val learned = word(id = 3, studyCount = 2, isCorrectLast = true)

        assertEquals(
            listOf(learned),
            StudyFilterMode.select(listOf(vague, wrong, learned), StudyFilterMode.LEARNED_RANDOM)
        )
    }

    @Test
    fun `CSV export round trips commas quotes and newlines`() {
        val source = listOf(
            word(
                id = 1,
                english = "day in, day out",
                japanese = "毎日\n欠かさず",
                tag = "\"phrase\"",
                pronunciation = ""
            )
        )

        val parsed = CsvParser.parse(CsvExporter.serialize(source).reader())

        assertEquals(listOf("day in, day out", "毎日\n欠かさず", "\"phrase\"", ""), parsed.single())
    }

    @Test
    fun `multiple choice questions always contain four distinct options`() {
        val source = listOf(
            word(1, "apple", "りんご"),
            word(2, "pomme", "りんご")
        )

        val question = QuizQuestionFactory.create(
            sessionWords = source.take(1),
            allWords = source,
            directionForward = true,
            isMultipleChoice = true
        ).single()

        assertEquals(4, question.choices.size)
        assertEquals(4, question.choices.distinct().size)
        assertTrue(question.correctAnswer in question.choices)
    }

    private fun word(
        id: Long,
        english: String = "term",
        japanese: String = "意味",
        tag: String = "",
        pronunciation: String = "",
        studyCount: Int = 0,
        isCorrectLast: Boolean = true
    ) = Word(
        id = id,
        groupId = 1,
        english = english,
        japanese = japanese,
        tag = tag,
        pronunciation = pronunciation,
        studyCount = studyCount,
        isCorrectLast = isCorrectLast
    )
}
