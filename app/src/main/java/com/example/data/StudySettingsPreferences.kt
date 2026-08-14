package com.example.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.domain.StudyFilterMode
import com.example.domain.StudySettings

class StudySettingsPreferences(private val preferences: SharedPreferences) {
    fun load(groupId: Long, legacy: StudySettings): StudySettings {
        val prefix = prefix(groupId)
        if (!preferences.getBoolean("${prefix}saved", false)) {
            return StudySettings.normalize(legacy).also { save(groupId, it) }
        }
        return StudySettings.normalize(
            StudySettings(
                directionForward = preferences.getBoolean("${prefix}directionForward", true),
                multipleChoice = preferences.getBoolean("${prefix}multipleChoice", true),
                filterMode = preferences.getString("${prefix}filterMode", StudyFilterMode.RECOMMEND)
                    ?: StudyFilterMode.RECOMMEND,
                selectedTag = preferences.getString("${prefix}selectedTag", "すべて") ?: "すべて",
                rangeStart = preferences.getInt("${prefix}rangeStart", 1),
                rangeEnd = preferences.getInt("${prefix}rangeEnd", -1),
                useRangeConstraint = preferences.getBoolean("${prefix}useRangeConstraint", false),
                quizCount = preferences.getInt("${prefix}quizCount", StudySettings.DEFAULT_QUIZ_COUNT)
            )
        )
    }

    fun save(groupId: Long, value: StudySettings) {
        val settings = StudySettings.normalize(value)
        val prefix = prefix(groupId)
        preferences.edit {
            putBoolean("${prefix}saved", true)
            putBoolean("${prefix}directionForward", settings.directionForward)
            putBoolean("${prefix}multipleChoice", settings.multipleChoice)
            putString("${prefix}filterMode", settings.filterMode)
            putString("${prefix}selectedTag", settings.selectedTag)
            putInt("${prefix}rangeStart", settings.rangeStart)
            putInt("${prefix}rangeEnd", settings.rangeEnd)
            putBoolean("${prefix}useRangeConstraint", settings.useRangeConstraint)
            putInt("${prefix}quizCount", settings.quizCount)
        }
    }

    fun delete(groupId: Long) {
        val prefix = prefix(groupId)
        preferences.edit {
            preferences.all.keys.filter { it.startsWith(prefix) }.forEach(::remove)
        }
    }

    private fun prefix(groupId: Long) = "studySettings.$groupId."
}
