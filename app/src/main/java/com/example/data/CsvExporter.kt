package com.example.data

object CsvExporter {
    fun escape(field: String): String {
        val escaped = field.replace("\"", "\"\"")
        return if (field.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun serializeRows(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(
                row.joinToString(",", transform = ::escape)
            )
            append('\n')
        }
    }

    fun serialize(words: List<Word>): String = serializeRows(
        words.map { word -> listOf(word.english, word.japanese, word.tag, word.pronunciation) }
    )
}
