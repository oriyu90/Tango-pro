package com.example.data

import java.io.PushbackReader
import java.io.Reader

/** RFC 4180 compatible CSV parser with a hard record limit. */
object CsvParser {
    fun parse(reader: Reader, maxRecords: Int = 100_000): List<List<String>> {
        require(maxRecords > 0) { "maxRecords must be positive" }

        val input = PushbackReader(reader, 1)
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var recordHasContent = false

        fun finishRecord() {
            record.add(field.toString())
            field.setLength(0)
            if (recordHasContent || record.any { it.isNotEmpty() }) {
                if (records.size >= maxRecords) {
                    throw IllegalArgumentException("CSVは最大${maxRecords}行までです。")
                }
                records.add(record)
            }
            record = mutableListOf()
            recordHasContent = false
        }

        while (true) {
            val value = input.read()
            if (value == -1) break
            val char = value.toChar()

            if (inQuotes) {
                if (char == '"') {
                    val next = input.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        if (next != -1) input.unread(next)
                    }
                } else {
                    field.append(char)
                }
                recordHasContent = true
                continue
            }

            when (char) {
                '\uFEFF' -> {
                    if (records.isNotEmpty() || record.isNotEmpty() || field.isNotEmpty()) {
                        field.append(char)
                        recordHasContent = true
                    }
                }
                '"' -> {
                    if (field.isEmpty()) {
                        inQuotes = true
                    } else {
                        field.append(char)
                    }
                    recordHasContent = true
                }
                ',' -> {
                    record.add(field.toString())
                    field.setLength(0)
                    recordHasContent = true
                }
                '\n' -> finishRecord()
                '\r' -> {
                    val next = input.read()
                    if (next != '\n'.code && next != -1) input.unread(next)
                    finishRecord()
                }
                else -> {
                    field.append(char)
                    if (!char.isWhitespace()) recordHasContent = true
                }
            }
        }

        if (inQuotes) {
            throw IllegalArgumentException("CSVの引用符が閉じられていません。")
        }
        if (field.isNotEmpty() || record.isNotEmpty() || recordHasContent) finishRecord()

        return records.mapIndexed { index, fields ->
            if (index == 0 && fields.isNotEmpty()) {
                fields.toMutableList().also { it[0] = it[0].removePrefix("\uFEFF") }
            } else {
                fields
            }
        }
    }
}
