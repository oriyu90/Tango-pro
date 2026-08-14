package com.example.data

import androidx.room.withTransaction
import com.example.domain.StudyRecordMerger
import java.io.InputStream
import java.io.OutputStream

class StudyArchiveService(
    private val database: AppDatabase,
    private val wordDao: WordDao
) {
    suspend fun exportTo(output: OutputStream, appVersion: String, exportedAt: Long = System.currentTimeMillis()) {
        val groups = wordDao.getGroupsDirect().map { group ->
            val words = wordDao.getWordsDirect(group.id)
            StudyArchiveExportGroup(
                name = group.name,
                language = group.language,
                words = words.mapIndexed { row, word -> word.toArchiveWord(row) }
            )
        }
        StudyArchiveCodec.write(groups, appVersion, exportedAt, output)
    }

    suspend fun importFrom(input: InputStream): StudyArchiveImportSummary {
        // Parse and validate the complete archive before opening a write transaction.
        val importedGroups = StudyArchiveCodec.read(input)
        return database.withTransaction {
            val currentGroups = wordDao.getGroupsDirect()
            val usedNames = currentGroups.mapTo(mutableSetOf()) { it.name }
            var nextSortOrder = currentGroups.maxOfOrNull { it.sortOrder } ?: -1
            val candidates = currentGroups.map { group ->
                val words = wordDao.getWordsDirect(group.id)
                Candidate(group, words.toMutableList(), canonicalCsv(words))
            }.toMutableList()

            var mergedGroups = 0
            var addedGroups = 0
            var mergedWords = 0
            var addedWords = 0
            var firstAddedGroupId: Long? = null

            importedGroups.forEach { incoming ->
                val matchIndex = candidates.indexOfFirst { it.canonicalCsv == incoming.canonicalCsv }
                if (matchIndex >= 0) {
                    val candidate = candidates[matchIndex]
                    require(candidate.words.size == incoming.words.size) { "一致したCSVの行数が異なります。" }
                    val updated = candidate.words.mapIndexed { row, localWord ->
                        val merged = StudyRecordMerger.merge(
                            localWord.toArchiveRecord(row),
                            incoming.words[row].progress
                        )
                        localWord.copy(
                            studyCount = merged.studyCount,
                            isCorrectLast = merged.isCorrectLast,
                            lastStudiedAt = merged.lastStudiedAt
                        )
                    }
                    wordDao.updateWords(updated)
                    candidates[matchIndex] = candidate.copy(words = updated.toMutableList())
                    mergedGroups++
                    mergedWords += updated.size
                } else {
                    nextSortOrder++
                    val groupId = wordDao.insertGroup(
                        StudyGroup(
                            name = uniqueName(incoming.name, usedNames),
                            language = incoming.language,
                            sortOrder = nextSortOrder
                        )
                    )
                    val words = incoming.words.map { word ->
                        Word(
                            groupId = groupId,
                            english = word.term,
                            japanese = word.meaning,
                            tag = word.tag,
                            pronunciation = word.pronunciation,
                            studyCount = word.progress.studyCount,
                            isCorrectLast = word.progress.isCorrectLast,
                            lastStudiedAt = word.progress.lastStudiedAt
                        )
                    }
                    for (chunk in words.chunked(5_000)) {
                        wordDao.insertWords(chunk)
                    }
                    val group = wordDao.getGroupById(groupId) ?: error("追加した単語帳を読み出せません。")
                    val inserted = wordDao.getWordsDirect(groupId).toMutableList()
                    candidates.add(Candidate(group, inserted, incoming.canonicalCsv))
                    if (firstAddedGroupId == null) firstAddedGroupId = groupId
                    addedGroups++
                    addedWords += words.size
                }
            }

            StudyArchiveImportSummary(
                mergedGroups = mergedGroups,
                addedGroups = addedGroups,
                mergedWords = mergedWords,
                addedWords = addedWords,
                firstAddedGroupId = firstAddedGroupId
            )
        }
    }

    private fun canonicalCsv(words: List<Word>): String = CsvExporter.serialize(words)

    private fun uniqueName(base: String, usedNames: MutableSet<String>): String {
        val normalized = base.trim().ifBlank { "インポートした単語帳" }
        if (usedNames.add(normalized)) return normalized
        var suffix = 2
        while (!usedNames.add("$normalized $suffix")) suffix++
        return "$normalized $suffix"
    }

    private fun Word.toArchiveRecord(row: Int) = StudyArchiveRecord(
        row = row,
        studyCount = studyCount,
        isCorrectLast = isCorrectLast,
        lastStudiedAt = lastStudiedAt
    )

    private fun Word.toArchiveWord(row: Int) = StudyArchiveWord(
        term = english,
        meaning = japanese,
        tag = tag,
        pronunciation = pronunciation,
        progress = toArchiveRecord(row)
    )

    private data class Candidate(
        val group: StudyGroup,
        val words: MutableList<Word>,
        val canonicalCsv: String
    )
}
