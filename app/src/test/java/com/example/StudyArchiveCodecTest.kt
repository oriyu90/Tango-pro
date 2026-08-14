package com.example

import com.example.data.StudyArchiveCodec
import com.example.data.StudyArchiveExportGroup
import com.example.data.StudyArchiveRecord
import com.example.data.StudyArchiveWord
import com.example.domain.StudyRecordMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StudyArchiveCodecTest {
    @Test
    fun `French and Portuguese language metadata round trips`() {
        val source = listOf(
            StudyArchiveExportGroup("français", "fr", listOf(word(0, "bonjour", "こんにちは"))),
            StudyArchiveExportGroup("português", "pt", listOf(word(0, "olá", "こんにちは")))
        )

        val imported = StudyArchiveCodec.fromByteArray(StudyArchiveCodec.toByteArray(source))

        assertEquals(listOf("fr", "pt"), imported.map { it.language })
    }

    @Test
    fun `archive round trip preserves CSV and progress`() {
        val source = listOf(
            StudyArchiveExportGroup(
                name = "英語,重要",
                language = "en",
                words = listOf(
                    word(0, "day in, day out", "毎日\n欠かさず", "phrase", "", 3, false, 1234)
                )
            )
        )

        val imported = StudyArchiveCodec.fromByteArray(StudyArchiveCodec.toByteArray(source)).single()

        assertEquals(source.single().name, imported.name)
        assertEquals("day in, day out", imported.words.single().term)
        assertEquals("毎日\n欠かさず", imported.words.single().meaning)
        assertEquals(3, imported.words.single().progress.studyCount)
        assertFalse(imported.words.single().progress.isCorrectLast)
    }

    @Test
    fun `unsafe ZIP path is rejected before import`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../manifest.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            StudyArchiveCodec.fromByteArray(output.toByteArray())
        }
    }

    @Test
    fun `CSV tampering is rejected by SHA-256 validation`() {
        val valid = StudyArchiveCodec.toByteArray(
            listOf(StudyArchiveExportGroup("book", "en", listOf(word(0, "apple", "りんご"))))
        )
        val tampered = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { target ->
                ZipInputStream(ByteArrayInputStream(valid)).use { source ->
                    while (true) {
                        val entry = source.nextEntry ?: break
                        target.putNextEntry(ZipEntry(entry.name))
                        val bytes = source.readBytes()
                        target.write(if (entry.name.endsWith("words.csv")) bytes + 'x'.code.toByte() else bytes)
                        target.closeEntry()
                    }
                }
            }
        }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            StudyArchiveCodec.fromByteArray(tampered)
        }
    }

    @Test
    fun `studied record wins over unstudied and latest result wins between studied records`() {
        val studied = StudyArchiveRecord(0, 3, true, 100)
        val unstudied = StudyArchiveRecord(0, 0, true, 0)
        val newerMistake = StudyArchiveRecord(0, 2, false, 200)

        assertEquals(studied, StudyRecordMerger.merge(studied, unstudied))
        val merged = StudyRecordMerger.merge(studied, newerMistake)
        assertEquals(3, merged.studyCount)
        assertFalse(merged.isCorrectLast)
        assertEquals(200, merged.lastStudiedAt)
    }

    @Test
    fun `producer output can be parsed again`() {
        val valid = StudyArchiveCodec.toByteArray(
            listOf(
                StudyArchiveExportGroup("book", "en", listOf(word(0, "apple", "りんご"))),
                StudyArchiveExportGroup("livre français", "fr", listOf(word(0, "bonjour", "こんにちは")))
            )
        )
        assertTrue(valid.isNotEmpty())
        // Canonical enforcement is covered by parsing an externally formatted archive
        // in the integration fixture tests; this assertion guards the basic producer.
        assertEquals(2, StudyArchiveCodec.fromByteArray(valid).size)
        System.getenv("TANGO_ANDROID_FIXTURE_OUTPUT")?.let { File(it).writeBytes(valid) }
    }

    @Test
    fun `macOS archive fixture is Android compatible when supplied`() {
        val path = System.getenv("TANGO_ARCHIVE_FIXTURE") ?: return
        val imported = StudyArchiveCodec.fromByteArray(File(path).readBytes())
        assertEquals(2, imported.size)
        assertTrue(imported.all { it.words.isNotEmpty() })
    }

    private fun word(
        row: Int,
        term: String,
        meaning: String,
        tag: String = "",
        pronunciation: String = "",
        studyCount: Int = 0,
        isCorrectLast: Boolean = true,
        lastStudiedAt: Long = 0
    ) = StudyArchiveWord(
        term,
        meaning,
        tag,
        pronunciation,
        StudyArchiveRecord(row, studyCount, isCorrectLast, lastStudiedAt)
    )
}
