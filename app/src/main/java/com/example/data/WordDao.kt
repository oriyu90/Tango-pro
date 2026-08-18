package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class GroupRoundProgress(
    val groupId: Long,
    val minimumStudyCount: Int
)

@Dao
interface WordDao {
    @Query("SELECT * FROM study_groups ORDER BY sortOrder ASC, createdAt DESC")
    fun getGroups(): Flow<List<StudyGroup>>

    @Query("SELECT groupId, MIN(studyCount) AS minimumStudyCount FROM words GROUP BY groupId")
    fun getGroupRoundProgress(): Flow<List<GroupRoundProgress>>

    @Query("SELECT * FROM study_groups ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getGroupsDirect(): List<StudyGroup>

    @Query("SELECT * FROM study_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): StudyGroup?

    @Query("SELECT * FROM words WHERE groupId = :groupId ORDER BY id ASC")
    fun getWords(groupId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE groupId = :groupId ORDER BY id ASC")
    suspend fun getWordsDirect(groupId: Long): List<Word>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: StudyGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateGroup(group: StudyGroup)

    @Update
    suspend fun updateGroups(groups: List<StudyGroup>)

    @Update
    suspend fun updateWord(word: Word)

    @Query(
        """UPDATE words
           SET studyCount = studyCount + 1,
               isCorrectLast = :isCorrect,
               lastStudiedAt = :studiedAt
           WHERE id = :wordId"""
    )
    suspend fun recordStudyResult(wordId: Long, isCorrect: Boolean, studiedAt: Long): Int

    @Update
    suspend fun updateWords(words: List<Word>)

    @Query("DELETE FROM study_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM words WHERE groupId = :groupId")
    suspend fun deleteWordsByGroupId(groupId: Long)

    @Query("UPDATE words SET studyCount = 0, isCorrectLast = 1, lastStudiedAt = 0 WHERE groupId = :groupId")
    suspend fun resetGroupProgress(groupId: Long)

    @Transaction
    suspend fun deleteGroupAndWords(groupId: Long) {
        deleteWordsByGroupId(groupId)
        deleteGroup(groupId)
    }
}
