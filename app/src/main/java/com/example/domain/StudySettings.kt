package com.example.domain

data class StudySettings(
    val directionForward: Boolean = true,
    val multipleChoice: Boolean = true,
    val filterMode: String = StudyFilterMode.RECOMMEND,
    val selectedTag: String = "すべて",
    val rangeStart: Int = 1,
    val rangeEnd: Int = -1,
    val useRangeConstraint: Boolean = false,
    val quizCount: Int = DEFAULT_QUIZ_COUNT
) {
    companion object {
        const val QUIZ_COUNT_ALL = 100_000
        const val DEFAULT_QUIZ_COUNT = 10
        val allowedQuizCounts = setOf(5, 10, 20, 50, QUIZ_COUNT_ALL)

        fun normalize(settings: StudySettings): StudySettings {
            val multipleChoice = settings.multipleChoice
            return settings.copy(
                directionForward = multipleChoice && settings.directionForward,
                filterMode = StudyFilterMode.normalize(settings.filterMode),
                selectedTag = settings.selectedTag.ifBlank { "すべて" },
                rangeStart = settings.rangeStart.coerceAtLeast(1),
                rangeEnd = settings.rangeEnd.takeIf { it == -1 || it >= 1 } ?: -1,
                quizCount = settings.quizCount.takeIf { it in allowedQuizCounts } ?: DEFAULT_QUIZ_COUNT
            )
        }
    }
}
