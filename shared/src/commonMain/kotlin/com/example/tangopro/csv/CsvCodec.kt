package com.example.tangopro.csv

class CsvDecoder(private val maxRecords: Int = 100_000) {
    private val records = mutableListOf<List<String>>()
    private var record = mutableListOf<String>()
    private val field = StringBuilder()
    private var inQuotes = false
    private var quotePending = false
    private var skipLf = false
    private var recordHasContent = false
    private var firstCharacter = true

    init {
        require(maxRecords > 0) { "maxRecords must be positive" }
    }

    fun feed(chunk: String) {
        for (raw in chunk) {
            if (firstCharacter) {
                firstCharacter = false
                if (raw == '\uFEFF') continue
            }
            if (skipLf) {
                skipLf = false
                if (raw == '\n') continue
            }
            if (inQuotes) {
                if (quotePending) {
                    if (raw == '"') {
                        field.append('"')
                        quotePending = false
                        recordHasContent = true
                        continue
                    }
                    inQuotes = false
                    quotePending = false
                    processUnquoted(raw)
                    continue
                }
                if (raw == '"') quotePending = true else field.append(raw)
                recordHasContent = true
                continue
            }
            processUnquoted(raw)
        }
    }

    fun finish(): List<List<String>> {
        if (quotePending) {
            inQuotes = false
            quotePending = false
        }
        require(!inQuotes) { "CSVの引用符が閉じられていません。" }
        if (field.isNotEmpty() || record.isNotEmpty() || recordHasContent) finishRecord()
        return records.toList()
    }

    private fun processUnquoted(char: Char) {
        when (char) {
            '"' -> if (field.isEmpty()) inQuotes = true else field.append(char)
            ',' -> {
                record.add(field.toString())
                field.clear()
                recordHasContent = true
            }
            '\n' -> finishRecord()
            '\r' -> {
                finishRecord()
                skipLf = true
            }
            else -> {
                field.append(char)
                if (!char.isWhitespace()) recordHasContent = true
            }
        }
    }

    private fun finishRecord() {
        record.add(field.toString())
        field.clear()
        if (recordHasContent || record.any(String::isNotEmpty)) {
            require(records.size < maxRecords) { "CSVは最大${maxRecords}行までです。" }
            records += record
        }
        record = mutableListOf()
        recordHasContent = false
    }
}

object CsvCodec {
    fun parse(text: String, maxRecords: Int = 100_000): List<List<String>> =
        CsvDecoder(maxRecords).also { it.feed(text) }.finish()

    fun escape(field: String): String {
        val escaped = field.replace("\"", "\"\"")
        return if (field.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) "\"$escaped\"" else escaped
    }

    fun serializeRows(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(row.joinToString(",", transform = ::escape))
            append('\n')
        }
    }
}
