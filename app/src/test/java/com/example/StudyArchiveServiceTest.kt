package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.StudyArchiveCodec
import com.example.data.StudyArchiveExportGroup
import com.example.data.StudyArchiveRecord
import com.example.data.StudyArchiveService
import com.example.data.StudyArchiveWord
import com.example.data.StudyGroup
import com.example.data.Word
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyArchiveServiceTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `matching CSV merges progress and mismatching CSV creates a new group`() = runTest {
        val dao = database.wordDao()
        val groupId = dao.insertGroup(StudyGroup(name = "local", language = "en", sortOrder = 0))
        dao.insertWords(
            listOf(
                Word(groupId = groupId, english = "apple", japanese = "りんご", studyCount = 3,
                    isCorrectLast = true, lastStudiedAt = 100),
                Word(groupId = groupId, english = "book", japanese = "本")
            )
        )
        val matching = StudyArchiveExportGroup(
            "remote-name",
            "en",
            listOf(
                archiveWord(0, "apple", "りんご", 2, false, 200),
                archiveWord(1, "book", "本", 1, true, 150)
            )
        )
        val changedCsv = StudyArchiveExportGroup(
            "local",
            "en",
            listOf(archiveWord(0, "apple", "林檎", 4, true, 300))
        )
        val bytes = StudyArchiveCodec.toByteArray(listOf(matching, changedCsv))

        val summary = StudyArchiveService(database, dao).importFrom(bytes.inputStream())

        assertEquals(1, summary.mergedGroups)
        assertEquals(1, summary.addedGroups)
        assertEquals(2, dao.getGroupsDirect().size)
        val merged = dao.getWordsDirect(groupId)
        assertEquals(3, merged[0].studyCount)
        assertFalse(merged[0].isCorrectLast)
        assertEquals(200, merged[0].lastStudiedAt)
        assertEquals(1, merged[1].studyCount)
        val addedGroup = dao.getGroupsDirect().first { it.id != groupId }
        assertTrue(addedGroup.name.startsWith("local"))
        assertEquals("林檎", dao.getWordsDirect(addedGroup.id).single().japanese)
    }

    private fun archiveWord(
        row: Int,
        term: String,
        meaning: String,
        count: Int,
        correct: Boolean,
        time: Long
    ) = StudyArchiveWord(term, meaning, "", "", StudyArchiveRecord(row, count, correct, time))
}
