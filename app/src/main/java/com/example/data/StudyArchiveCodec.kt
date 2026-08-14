package com.example.data

import com.squareup.moshi.Moshi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object StudyArchiveCodec {
    private const val MANIFEST_PATH = "manifest.json"
    private const val MAX_GROUPS = 500
    private const val MAX_WORDS_PER_GROUP = 100_000
    private const val MAX_ENTRIES = 1 + MAX_GROUPS * 2
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 256L * 1024L * 1024L
    private val safeId = Regex("group-[0-9]{4}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val moshi = Moshi.Builder().build()
    private val manifestAdapter = moshi.adapter(StudyArchiveManifest::class.java)
    private val progressAdapter = moshi.adapter(StudyArchiveProgress::class.java)

    fun write(
        groups: List<StudyArchiveExportGroup>,
        appVersion: String,
        exportedAtEpochMillis: Long,
        output: OutputStream
    ) {
        require(groups.size <= MAX_GROUPS) { "単語帳は最大${MAX_GROUPS}冊までです。" }
        var totalBytes = 0L
        val prepared = groups.mapIndexed { index, group ->
            require(group.name.isNotBlank() && group.name.length <= 200) { "単語帳名が不正です。" }
            require(group.language in setOf("en", "zh", "none")) { "未対応の言語です。" }
            require(group.words.size <= MAX_WORDS_PER_GROUP) { "1冊あたり最大${MAX_WORDS_PER_GROUP}語までです。" }
            validateWordRecords(group.words)
            val id = "group-${(index + 1).toString().padStart(4, '0')}"
            val csv = canonicalCsv(group.words)
            val hash = sha256(csv.toByteArray(Charsets.UTF_8))
            val progress = StudyArchiveProgress(
                version = STUDY_ARCHIVE_VERSION,
                csvSha256 = hash,
                records = group.words.mapIndexed { row, word -> word.progress.copy(row = row) }
            )
            val csvBytes = csv.toByteArray(Charsets.UTF_8)
            val progressBytes = progressAdapter.toJson(progress).toByteArray(Charsets.UTF_8)
            require(csvBytes.size.toLong() <= MAX_ENTRY_BYTES && progressBytes.size.toLong() <= MAX_ENTRY_BYTES) {
                "ZIP内のファイルが大きすぎます。"
            }
            totalBytes += csvBytes.size.toLong() + progressBytes.size.toLong()
            require(totalBytes <= MAX_TOTAL_BYTES) { "ZIPの展開サイズが上限を超えています。" }
            PreparedGroup(
                manifest = StudyArchiveGroupManifest(
                    id = id,
                    name = group.name,
                    language = group.language,
                    csvPath = "groups/$id/words.csv",
                    progressPath = "groups/$id/progress.json",
                    csvSha256 = hash
                ),
                csv = csvBytes,
                progress = progressBytes
            )
        }
        val manifest = StudyArchiveManifest(
            format = STUDY_ARCHIVE_FORMAT,
            version = STUDY_ARCHIVE_VERSION,
            exportedAtEpochMillis = exportedAtEpochMillis,
            appVersion = appVersion,
            groups = prepared.map { it.manifest }
        )

        val manifestBytes = manifestAdapter.toJson(manifest).toByteArray(Charsets.UTF_8)
        require(manifestBytes.size.toLong() <= MAX_ENTRY_BYTES && totalBytes + manifestBytes.size.toLong() <= MAX_TOTAL_BYTES) {
            "manifestまたはZIP全体が大きすぎます。"
        }
        ZipOutputStream(output.buffered()).use { zip ->
            put(zip, MANIFEST_PATH, manifestBytes)
            prepared.forEach { group ->
                put(zip, group.manifest.csvPath, group.csv)
                put(zip, group.manifest.progressPath, group.progress)
            }
        }
    }

    fun read(input: InputStream): List<StudyArchiveImportedGroup> {
        val entries = readEntries(input)
        val manifestBytes = entries[MANIFEST_PATH] ?: throw IllegalArgumentException("manifest.jsonがありません。")
        val manifest = manifestAdapter.fromJson(decodeUtf8(manifestBytes, "manifest.json"))
            ?: throw IllegalArgumentException("manifest.jsonが不正です。")
        require(manifest.format == STUDY_ARCHIVE_FORMAT) { "Tango proの学習記録ZIPではありません。" }
        require(manifest.version == STUDY_ARCHIVE_VERSION) { "未対応のZIP形式versionです。" }
        require(manifest.exportedAtEpochMillis >= 0) { "書き出し日時が不正です。" }
        require(manifest.groups.size <= MAX_GROUPS) { "単語帳数が上限を超えています。" }

        val ids = mutableSetOf<String>()
        val expectedPaths = mutableSetOf(MANIFEST_PATH)
        val imported = manifest.groups.map { group ->
            require(safeId.matches(group.id) && ids.add(group.id)) { "単語帳IDが不正または重複しています。" }
            require(group.name.isNotBlank() && group.name.length <= 200) { "単語帳名が不正です。" }
            require(group.language in setOf("en", "zh", "none")) { "未対応の言語です。" }
            require(group.csvPath == "groups/${group.id}/words.csv") { "CSV pathが不正です。" }
            require(group.progressPath == "groups/${group.id}/progress.json") { "学習記録pathが不正です。" }
            require(sha256Pattern.matches(group.csvSha256)) { "CSV hashが不正です。" }
            expectedPaths.add(group.csvPath)
            expectedPaths.add(group.progressPath)

            val csvBytes = entries[group.csvPath] ?: throw IllegalArgumentException("CSVが不足しています。")
            require(sha256(csvBytes) == group.csvSha256) { "CSVのSHA-256が一致しません。" }
            val csvText = csvBytes.toString(Charsets.UTF_8)
            require(csvText.toByteArray(Charsets.UTF_8).contentEquals(csvBytes)) { "CSVはUTF-8ではありません。" }
            val rows = CsvParser.parse(csvText.reader(), MAX_WORDS_PER_GROUP)
            require(rows.all { it.size == 4 && it[0].isNotBlank() && it[1].isNotBlank() }) {
                "アーカイブCSVは4列の有効な単語データである必要があります。"
            }

            val progressBytes = entries[group.progressPath]
                ?: throw IllegalArgumentException("学習記録が不足しています。")
            val progress = progressAdapter.fromJson(decodeUtf8(progressBytes, group.progressPath))
                ?: throw IllegalArgumentException("学習記録JSONが不正です。")
            require(progress.version == STUDY_ARCHIVE_VERSION) { "未対応の学習記録versionです。" }
            require(progress.csvSha256 == group.csvSha256) { "学習記録とCSVのhashが一致しません。" }
            require(progress.records.size == rows.size) { "CSVと学習記録の行数が一致しません。" }
            require(progress.records.map { it.row }.toSet().size == rows.size) { "学習記録の行番号が重複しています。" }
            val records = progress.records.sortedBy { it.row }
            require(records.indices.all { records[it].row == it }) { "学習記録の行番号が連続していません。" }
            validateProgressRecords(records)

            val words = rows.mapIndexed { row, fields ->
                StudyArchiveWord(fields[0], fields[1], fields[2], fields[3], records[row])
            }
            val canonical = canonicalCsv(words)
            require(canonical == csvText) { "CSVがTango proの正規形式ではありません。" }
            StudyArchiveImportedGroup(group.name, group.language, words, canonical)
        }
        require(entries.keys == expectedPaths) { "manifestにないファイル、または不足ファイルがあります。" }
        return imported
    }

    fun canonicalCsv(words: List<StudyArchiveWord>): String =
        CsvExporter.serializeRows(words.map(StudyArchiveWord::csvFields))

    fun toByteArray(groups: List<StudyArchiveExportGroup>, appVersion: String = "test"): ByteArray =
        ByteArrayOutputStream().use { output ->
            write(groups, appVersion, 0, output)
            output.toByteArray()
        }

    fun fromByteArray(bytes: ByteArray): List<StudyArchiveImportedGroup> =
        ByteArrayInputStream(bytes).use(::read)

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                validatePath(entry.name)
                require(entries.size < MAX_ENTRIES) { "ZIP内のファイル数が上限を超えています。" }
                require(entry.name !in entries) { "ZIP内のファイル名が重複しています。" }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entrySize = 0L
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entrySize += count
                    total += count
                    require(entrySize <= MAX_ENTRY_BYTES) { "ZIP内のファイルが大きすぎます。" }
                    require(total <= MAX_TOTAL_BYTES) { "ZIPの展開サイズが上限を超えています。" }
                    output.write(buffer, 0, count)
                }
                entries[entry.name] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path) { "ZIP entry pathが不正です。" }
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "ZIP entry pathが不正です。" }
    }

    private fun decodeUtf8(bytes: ByteArray, label: String): String {
        val text = bytes.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "${label}はUTF-8ではありません。" }
        return text
    }

    private fun validateWordRecords(words: List<StudyArchiveWord>) =
        validateProgressRecords(words.map { it.progress })

    private fun validateProgressRecords(records: List<StudyArchiveRecord>) {
        require(records.all { it.studyCount >= 0 && it.lastStudiedAt >= 0 }) { "学習記録に負の値があります。" }
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class PreparedGroup(
        val manifest: StudyArchiveGroupManifest,
        val csv: ByteArray,
        val progress: ByteArray
    )
}
