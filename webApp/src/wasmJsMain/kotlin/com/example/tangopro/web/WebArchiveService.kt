package com.example.tangopro.web

import com.example.tangopro.csv.CsvCodec
import com.example.tangopro.domain.StudyLanguage
import com.example.tangopro.web.data.WebWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val ARCHIVE_FORMAT = "tango-pro-study-archive"
private const val ARCHIVE_VERSION = 1
private val groupIdPattern = Regex("group-[0-9]{4}")
private val hashPattern = Regex("[0-9a-f]{64}")

class WebArchiveService(private val repository: WebRepository) {
    suspend fun export(filename: String = "tango-pro-study-records.zip") {
        val groups = repository.groupsDirect()
        require(groups.size <= 500) { "単語帳は最大500冊までです。" }
        val entries = mutableListOf<JsonElement>()
        val manifests = mutableListOf<JsonElement>()
        groups.forEachIndexed { index, group ->
            val words = repository.wordsDirect(group.id)
            require(words.size <= 100_000) { "1冊あたり最大100,000語までです。" }
            val id = "group-${(index + 1).toString().padStart(4, '0')}"
            val csvPath = "groups/$id/words.csv"
            val progressPath = "groups/$id/progress.json"
            val csv = CsvCodec.serializeRows(words.map { listOf(it.term, it.meaning, it.tag, it.pronunciation) })
            val hash = sha256(csv)
            val progress = JsonObject(mapOf(
                "version" to JsonPrimitive(ARCHIVE_VERSION),
                "csvSha256" to JsonPrimitive(hash),
                "records" to JsonArray(words.mapIndexed { row, word ->
                    JsonObject(mapOf(
                        "row" to JsonPrimitive(row),
                        "studyCount" to JsonPrimitive(word.studyCount),
                        "isCorrectLast" to JsonPrimitive(word.isCorrectLast),
                        "lastStudiedAt" to JsonPrimitive(word.lastStudiedAt),
                    ))
                }),
            )).toString()
            entries += zipEntry(csvPath, csv)
            entries += zipEntry(progressPath, progress)
            manifests += JsonObject(mapOf(
                "id" to JsonPrimitive(id),
                "name" to JsonPrimitive(group.name),
                "language" to JsonPrimitive(group.language),
                "csvPath" to JsonPrimitive(csvPath),
                "progressPath" to JsonPrimitive(progressPath),
                "csvSha256" to JsonPrimitive(hash),
            ))
        }
        val manifest = JsonObject(mapOf(
            "format" to JsonPrimitive(ARCHIVE_FORMAT),
            "version" to JsonPrimitive(ARCHIVE_VERSION),
            "exportedAtEpochMillis" to JsonPrimitive(epochMillis().toLong()),
            "appVersion" to JsonPrimitive("2.1.0-web"),
            "groups" to JsonArray(manifests),
        )).toString()
        entries.add(0, zipEntry("manifest.json", manifest))
        createStudyArchive(filename, JsonArray(entries).toString())
    }

    suspend fun import(entriesJson: String): Pair<Int, Int> {
        return repository.importArchive(validateArchiveEntries(entriesJson))
    }

    private fun zipEntry(path: String, text: String) = JsonObject(mapOf("path" to JsonPrimitive(path), "text" to JsonPrimitive(text)))
}

internal suspend fun validateArchiveEntries(entriesJson: String): List<ImportedArchiveGroup> {
    val listed = Json.parseToJsonElement(entriesJson).jsonArray
    require(listed.size <= 1_001) { "ZIP内のファイル数が上限を超えています。" }
    val entries = linkedMapOf<String, String>()
    listed.forEach { element ->
        val item = element.jsonObject
        val path = item.string("path")
        validateArchivePath(path)
        require(entries.put(path, item.string("text")) == null) { "ZIP内のファイル名が重複しています。" }
    }
    val manifest = Json.parseToJsonElement(entries["manifest.json"] ?: error("manifest.jsonがありません。")).jsonObject
    require(manifest.string("format") == ARCHIVE_FORMAT) { "Tango proの学習記録ZIPではありません。" }
    require(manifest.number("version") == ARCHIVE_VERSION) { "未対応のZIP形式versionです。" }
    require(manifest.longNumber("exportedAtEpochMillis") >= 0) { "書き出し日時が不正です。" }
    val groups = manifest.array("groups")
    require(groups.size <= 500) { "単語帳数が上限を超えています。" }
    val expected = mutableSetOf("manifest.json")
    val ids = mutableSetOf<String>()
    val validated = groups.map { element ->
        val item = element.jsonObject
        val id = item.string("id")
        val name = item.string("name")
        val language = item.string("language")
        val csvPath = item.string("csvPath")
        val progressPath = item.string("progressPath")
        val expectedHash = item.string("csvSha256")
        require(groupIdPattern.matches(id) && ids.add(id)) { "単語帳IDが不正または重複しています。" }
        require(name.isNotBlank() && name.length <= 200) { "単語帳名が不正です。" }
        require(language in StudyLanguage.supportedCodes) { "未対応の言語です。" }
        require(csvPath == "groups/$id/words.csv" && progressPath == "groups/$id/progress.json") { "archive pathが不正です。" }
        require(hashPattern.matches(expectedHash)) { "CSV hashが不正です。" }
        expected += csvPath
        expected += progressPath
        val csv = entries[csvPath] ?: error("CSVが不足しています。")
        require(sha256(csv) == expectedHash) { "CSVのSHA-256が一致しません。" }
        val rows = CsvCodec.parse(csv, 100_000)
        require(rows.all { it.size == 4 && it[0].isNotBlank() && it[1].isNotBlank() }) { "アーカイブCSVは4列の有効な単語データである必要があります。" }
        require(CsvCodec.serializeRows(rows) == csv) { "CSVがTango proの正規形式ではありません。" }
        val progress = Json.parseToJsonElement(entries[progressPath] ?: error("学習記録が不足しています。")).jsonObject
        require(progress.number("version") == ARCHIVE_VERSION && progress.string("csvSha256") == expectedHash) { "学習記録とCSVの形式が一致しません。" }
        val records = progress.array("records").map { it.jsonObject }.sortedBy { it.number("row") }
        require(records.size == rows.size && records.indices.all { records[it].number("row") == it }) { "CSVと学習記録の行番号が一致しません。" }
        val words = rows.mapIndexed { row, fields ->
            val record = records[row]
            val count = record.number("studyCount")
            val studiedAt = record.longNumber("lastStudiedAt")
            require(count >= 0 && studiedAt >= 0) { "学習記録に負の値があります。" }
            WebWord(
                groupId = 0, english = fields[0], japanese = fields[1], tag = fields[2], pronunciation = fields[3],
                studyCount = count, isCorrectLast = record.boolean("isCorrectLast"), lastStudiedAt = studiedAt,
            )
        }
        ImportedArchiveGroup(name, language, words, csv)
    }
    require(entries.keys == expected) { "manifestにないファイル、または不足ファイルがあります。" }
    return validated
}

private fun validateArchivePath(path: String) {
    require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) { "ZIP entry pathが不正です。" }
    require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "ZIP entry pathが不正です。" }
}

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.int
private fun JsonObject.longNumber(key: String) = getValue(key).jsonPrimitive.long
private fun JsonObject.boolean(key: String) = getValue(key).jsonPrimitive.boolean
private fun JsonObject.array(key: String) = getValue(key).jsonArray
