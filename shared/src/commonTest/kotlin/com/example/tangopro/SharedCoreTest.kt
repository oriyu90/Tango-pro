package com.example.tangopro

import com.example.tangopro.csv.CsvCodec
import com.example.tangopro.csv.CsvDecoder
import com.example.tangopro.domain.AnswerNormalizer
import com.example.tangopro.domain.StudyProgress
import com.example.tangopro.domain.StudyRound
import com.example.tangopro.model.WordRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCoreTest {
    @Test
    fun progressAndRoundMatchV210() {
        val vague = word(1, true)
        val review = word(1, false)
        assertTrue(StudyProgress.isVague(vague))
        assertTrue(StudyProgress.needsReview(review))
        assertFalse(StudyProgress.isLearned(review))
        assertEquals(2, StudyRound.current(listOf(vague, review)))
    }

    @Test
    fun answerNormalizationUsesNfkc() {
        assertTrue(AnswerNormalizer.matches(" ＡＰＰＬＥ ", "apple"))
        assertFalse(AnswerNormalizer.matches("apple", "apples"))
    }

    @Test
    fun csvIsIncrementalAndStrictAtFinish() {
        val decoder = CsvDecoder()
        decoder.feed("\uFEFF\"day in,")
        decoder.feed(" day out\",毎日\r")
        decoder.feed("\nword,意味")
        assertEquals(listOf(listOf("day in, day out", "毎日"), listOf("word", "意味")), decoder.finish())
        assertFailsWith<IllegalArgumentException> { CsvCodec.parse("word,\"unfinished") }
    }

    @Test
    fun csvAcceptsOneHundredThousandAndRejectsTheNextRecord() {
        val atLimit = buildString {
            repeat(100_000) { append("word,").append(it).append('\n') }
        }
        assertEquals(100_000, CsvCodec.parse(atLimit, maxRecords = 100_000).size)
        assertFailsWith<IllegalArgumentException> {
            CsvCodec.parse(atLimit + "overflow,100001\n", maxRecords = 100_000)
        }
    }

    private fun word(count: Int, correct: Boolean) = WordRecord(
        term = "term",
        meaning = "意味",
        studyCount = count,
        isCorrectLast = correct,
    )
}
