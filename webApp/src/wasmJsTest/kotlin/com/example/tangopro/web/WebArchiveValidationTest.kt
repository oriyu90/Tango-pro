package com.example.tangopro.web

import com.example.tangopro.csv.CsvCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebArchiveValidationTest {
    @Test
    fun acceptsCanonicalArchiveAndProgress() = runTest {
        val archive = archiveEntries()
        val groups = validateArchiveEntries(archive)

        assertEquals(1, groups.size)
        assertEquals("互換テスト", groups.single().name)
        assertEquals(2, groups.single().words.single().studyCount)
        assertEquals(true, groups.single().words.single().isCorrectLast)
    }

    @Test
    fun rejectsTamperingUnknownVersionAndUnknownEntry() = runTest {
        assertFailsWith<IllegalArgumentException> {
            validateArchiveEntries(archiveEntries(csvOverride = "tampered,改ざん,,\n"))
        }
        assertFailsWith<IllegalArgumentException> {
            validateArchiveEntries(archiveEntries(version = 999))
        }
        assertFailsWith<IllegalArgumentException> {
            validateArchiveEntries(archiveEntries(extraEntry = entry("unknown.txt", "x")))
        }
    }

    @Test
    fun rejectsDangerousAndDuplicatePathsBeforeDatabaseMutation() = runTest {
        val dangerous = JsonArray(listOf(entry("../manifest.json", "{}"))).toString()
        assertFailsWith<IllegalArgumentException> { validateArchiveEntries(dangerous) }

        val duplicate = JsonArray(listOf(entry("manifest.json", "{}"), entry("manifest.json", "{}"))).toString()
        assertFailsWith<IllegalArgumentException> { validateArchiveEntries(duplicate) }
    }

    @Test
    fun rejectsBrokenRowMappingAndHash() = runTest {
        assertFailsWith<IllegalArgumentException> { validateArchiveEntries(archiveEntries(recordRow = 1)) }
        assertFailsWith<IllegalArgumentException> { validateArchiveEntries(archiveEntries(hashOverride = "0".repeat(64))) }
    }

    private suspend fun archiveEntries(
        version: Int = 1,
        csvOverride: String? = null,
        hashOverride: String? = null,
        recordRow: Int = 0,
        extraEntry: JsonObject? = null,
    ): String {
        val canonicalCsv = CsvCodec.serializeRows(listOf(listOf("apple", "りんご", "名詞", "æpl")))
        val hash = hashOverride ?: sha256(canonicalCsv)
        val manifest = JsonObject(mapOf(
            "format" to JsonPrimitive("tango-pro-study-archive"),
            "version" to JsonPrimitive(version),
            "exportedAtEpochMillis" to JsonPrimitive(1_786_000_000_000L),
            "appVersion" to JsonPrimitive("test"),
            "groups" to JsonArray(listOf(JsonObject(mapOf(
                "id" to JsonPrimitive("group-0001"),
                "name" to JsonPrimitive("互換テスト"),
                "language" to JsonPrimitive("en"),
                "csvPath" to JsonPrimitive("groups/group-0001/words.csv"),
                "progressPath" to JsonPrimitive("groups/group-0001/progress.json"),
                "csvSha256" to JsonPrimitive(hash),
            )))),
        )).toString()
        val progress = JsonObject(mapOf(
            "version" to JsonPrimitive(1),
            "csvSha256" to JsonPrimitive(hash),
            "records" to JsonArray(listOf(JsonObject(mapOf(
                "row" to JsonPrimitive(recordRow),
                "studyCount" to JsonPrimitive(2),
                "isCorrectLast" to JsonPrimitive(true),
                "lastStudiedAt" to JsonPrimitive(1_786_000_000_001L),
            )))),
        )).toString()
        return JsonArray(buildList {
            add(entry("manifest.json", manifest))
            add(entry("groups/group-0001/words.csv", csvOverride ?: canonicalCsv))
            add(entry("groups/group-0001/progress.json", progress))
            extraEntry?.let(::add)
        }).toString()
    }

    private fun entry(path: String, text: String) = JsonObject(mapOf(
        "path" to JsonPrimitive(path),
        "text" to JsonPrimitive(text),
    ))
}
