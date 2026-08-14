package com.example.domain

import com.example.data.StudyArchiveRecord

object StudyRecordMerger {
    /**
     * Keeps any studied record over an unstudied one. When both are studied,
     * the latest result decides correctness while the larger counter is kept.
     */
    fun merge(local: StudyArchiveRecord, incoming: StudyArchiveRecord): StudyArchiveRecord {
        if (local.studyCount == 0 && incoming.studyCount > 0) {
            return incoming.copy(row = local.row)
        }
        if (incoming.studyCount == 0) return local
        if (local.studyCount == 0) return incoming.copy(row = local.row)

        val latest = when {
            incoming.lastStudiedAt > local.lastStudiedAt -> incoming
            incoming.lastStudiedAt < local.lastStudiedAt -> local
            incoming.studyCount > local.studyCount -> incoming
            else -> local
        }
        return StudyArchiveRecord(
            row = local.row,
            studyCount = maxOf(local.studyCount, incoming.studyCount),
            isCorrectLast = latest.isCorrectLast,
            lastStudiedAt = maxOf(local.lastStudiedAt, incoming.lastStudiedAt)
        )
    }
}
