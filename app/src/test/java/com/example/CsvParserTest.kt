package com.example

import com.example.data.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvParserTest {
    @Test
    fun parsesQuotedCommaNewlineEscapedQuoteAndBom() {
        val csv = "\uFEFF\"day in, day out\",来る日も来る日も,Phrase\r\n" +
            "\"line\nbreak\",\"quote \"\"test\"\"\",tag\n"

        val rows = CsvParser.parse(csv.reader())

        assertEquals(2, rows.size)
        assertEquals(listOf("day in, day out", "来る日も来る日も", "Phrase"), rows[0])
        assertEquals(listOf("line\nbreak", "quote \"test\"", "tag"), rows[1])
    }

    @Test
    fun rejectsUnclosedQuoteWithoutProducingPartialData() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvParser.parse("word,\"unfinished\nnext,row".reader())
        }
    }
}
