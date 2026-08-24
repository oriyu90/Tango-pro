package com.example.tangopro.web

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import com.example.tangopro.csv.CsvCodec
import com.example.tangopro.domain.BundledGroupCatalog
import com.example.tangopro.domain.StudyLanguage
import com.example.tangopro.model.StudyGroupRecord
import com.example.tangopro.model.WordRecord
import com.example.tangopro.sqlite.createSQLiteWasmDriver
import com.example.tangopro.web.data.AppMeta
import com.example.tangopro.web.data.TangoWebDatabase
import com.example.tangopro.web.data.WebStudyGroup
import com.example.tangopro.web.data.WebWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WebRepository private constructor(private val database: TangoWebDatabase) {
    private val dao = database.wordDao()

    val groups: Flow<List<StudyGroupRecord>> = dao.observeGroups().map { groups -> groups.map(WebStudyGroup::record) }
    val rounds: Flow<Map<Long, Int>> = dao.observeRounds().map { rows ->
        rows.associate { it.groupId to (it.minimumStudyCount.coerceAtLeast(0) + 1) }
    }

    fun words(groupId: Long): Flow<List<WordRecord>> = dao.observeWords(groupId).map { words -> words.map(WebWord::record) }
    suspend fun wordsDirect(groupId: Long): List<WordRecord> = dao.getWords(groupId).map(WebWord::record)
    suspend fun groupsDirect(): List<StudyGroupRecord> = dao.getGroups().map(WebStudyGroup::record)
    suspend fun tags(groupId: Long): List<String> = dao.getTags(groupId)

    suspend fun bootstrapBundledGroups(onProgress: (Int, Int) -> Unit) {
        val existingNames = dao.getGroups().mapTo(mutableSetOf()) { it.name }
        BundledGroupCatalog.all.forEachIndexed { index, spec ->
            val marker = "bundled.${spec.id}"
            if (dao.getMeta(marker) == "1" || spec.displayName in existingNames) {
                dao.putMeta(AppMeta(marker, "1"))
            } else {
                importCsv(spec.displayName, spec.language, fetchBundledCsv(spec.assetFileName), appendAtEnd = true)
                existingNames += spec.displayName
                dao.putMeta(AppMeta(marker, "1"))
            }
            onProgress(index + 1, BundledGroupCatalog.all.size)
        }
    }

    suspend fun importCsv(name: String, language: String, text: String, appendAtEnd: Boolean = false): Long {
        val rows = CsvCodec.parse(text, maxRecords = 100_000).mapNotNull { fields ->
            if (fields.size < 2) return@mapNotNull null
            val term = fields[0].trim()
            val meaning = fields[1].trim()
            if (term.isBlank() || meaning.isBlank()) return@mapNotNull null
            listOf(term, meaning, fields.getOrElse(2) { "" }.trim(), fields.getOrElse(3) { "" }.trim())
        }
        require(rows.isNotEmpty()) { "有効な単語がありません。1列目と2列目を確認してください。" }
        require(name.isNotBlank() && name.length <= 200) { "単語帳名が不正です。" }
        val normalizedLanguage = StudyLanguage.normalize(language)
        return database.withWriteTransaction {
            val groups = dao.getGroups()
            val order = if (appendAtEnd) (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1
            else (groups.minOfOrNull { it.sortOrder } ?: 1) - 1
            val groupId = dao.insertGroup(
                WebStudyGroup(
                    name = name,
                    createdAt = epochMillis().toLong(),
                    language = normalizedLanguage,
                    sortOrder = order,
                )
            )
            rows.chunked(5_000).forEach { chunk ->
                dao.insertWords(chunk.map { fields ->
                    WebWord(
                        groupId = groupId,
                        english = fields[0],
                        japanese = fields[1],
                        tag = fields[2],
                        pronunciation = fields[3],
                    )
                })
            }
            groupId
        }
    }

    suspend fun updateGroup(group: StudyGroupRecord) {
        dao.updateGroup(group.entity())
    }

    suspend fun updateWord(word: WordRecord) {
        dao.updateWord(word.entity())
    }

    suspend fun deleteGroup(groupId: Long) = database.withWriteTransaction { dao.deleteGroupWithWords(groupId) }
    suspend fun resetProgress(groupId: Long) = dao.resetProgress(groupId)

    suspend fun recordResult(wordId: Long, correct: Boolean) {
        check(dao.recordStudyResult(wordId, correct, epochMillis().toLong()) == 1) { "学習結果を保存できませんでした。" }
    }

    suspend fun exportCsv(groupId: Long): String = CsvCodec.serializeRows(
        dao.getWords(groupId).map { listOf(it.english, it.japanese, it.tag, it.pronunciation) }
    )

    suspend fun combine(groupIds: List<Long>, name: String, language: String): Long {
        val ids = groupIds.distinct()
        require(ids.size >= 2) { "2冊以上を選択してください。" }
        val rows = ids.flatMap { dao.getWords(it) }
            .distinctBy { listOf(it.english, it.japanese, it.tag, it.pronunciation) }
        val csv = CsvCodec.serializeRows(rows.map { listOf(it.english, it.japanese, it.tag, it.pronunciation) })
        return importCsv(name, language, csv)
    }

    suspend fun importArchive(groups: List<ImportedArchiveGroup>): Pair<Int, Int> = database.withWriteTransaction {
        val localGroups = dao.getGroups().toMutableList()
        val localCsv = localGroups.associateWith { group ->
            CsvCodec.serializeRows(dao.getWords(group.id).map { listOf(it.english, it.japanese, it.tag, it.pronunciation) })
        }.toMutableMap()
        val names = localGroups.mapTo(mutableSetOf()) { it.name }
        var merged = 0
        var added = 0
        groups.forEach { incoming ->
            val matching = localCsv.entries.firstOrNull { it.value == incoming.canonicalCsv }?.key
            if (matching != null) {
                val localWords = dao.getWords(matching.id)
                incoming.words.forEachIndexed { index, word ->
                    val local = localWords[index]
                    val latestIncoming = when {
                        local.studyCount == 0 && word.studyCount > 0 -> word
                        word.studyCount == 0 -> null
                        local.studyCount == 0 -> word
                        word.lastStudiedAt > local.lastStudiedAt -> word
                        word.lastStudiedAt < local.lastStudiedAt -> null
                        word.studyCount > local.studyCount -> word
                        else -> null
                    }
                    val mergedWord = local.copy(
                        studyCount = maxOf(local.studyCount, word.studyCount),
                        isCorrectLast = latestIncoming?.isCorrectLast ?: local.isCorrectLast,
                        lastStudiedAt = maxOf(local.lastStudiedAt, word.lastStudiedAt),
                    )
                    if (mergedWord != local) dao.updateWord(mergedWord)
                }
                merged += 1
            } else {
                var resolvedName = incoming.name
                var suffix = 2
                while (resolvedName in names) resolvedName = "${incoming.name} ${suffix++}"
                names += resolvedName
                val sortOrder = (localGroups.minOfOrNull { it.sortOrder } ?: 1) - 1
                val groupId = dao.insertGroup(WebStudyGroup(0, resolvedName, epochMillis().toLong(), incoming.language, sortOrder))
                incoming.words.chunked(5_000).forEach { chunk ->
                    dao.insertWords(chunk.map { word -> word.copy(id = 0, groupId = groupId) })
                }
                val newGroup = WebStudyGroup(groupId, resolvedName, epochMillis().toLong(), incoming.language, sortOrder)
                localGroups += newGroup
                localCsv[newGroup] = incoming.canonicalCsv
                added += 1
            }
        }
        merged to added
    }

    companion object {
        fun open(databaseName: String = "tango-pro.db"): WebRepository {
            require(databaseName.isNotBlank() && '/' !in databaseName && '\\' !in databaseName) { "データベース名が不正です。" }
            val builder = if (databaseName == ":memory:") {
                Room.inMemoryDatabaseBuilder<TangoWebDatabase>()
            } else {
                Room.databaseBuilder<TangoWebDatabase>(databaseName)
            }
            val database = builder
                .setDriver(createSQLiteWasmDriver())
                .setSingleConnectionPool()
                .build()
            return WebRepository(database)
        }
    }
}

data class ImportedArchiveGroup(
    val name: String,
    val language: String,
    val words: List<WebWord>,
    val canonicalCsv: String,
)

private fun WebStudyGroup.record() = StudyGroupRecord(id, name, createdAt, language, sortOrder)
private fun WebWord.record() = WordRecord(
    id = id,
    groupId = groupId,
    term = english,
    meaning = japanese,
    tag = tag,
    pronunciation = pronunciation,
    studyCount = studyCount,
    isCorrectLast = isCorrectLast,
    lastStudiedAt = lastStudiedAt,
)
private fun StudyGroupRecord.entity() = WebStudyGroup(id, name, createdAt, language, sortOrder)
private fun WordRecord.entity() = WebWord(
    id, groupId, term, meaning, tag, pronunciation, studyCount, isCorrectLast, lastStudiedAt
)
