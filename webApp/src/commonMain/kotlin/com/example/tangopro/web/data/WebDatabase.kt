package com.example.tangopro.web.data

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_groups")
data class WebStudyGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val language: String = "en",
    val sortOrder: Int = 0,
)

@Entity(tableName = "words", indices = [Index(value = ["groupId"])])
data class WebWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val english: String,
    val japanese: String,
    val tag: String = "",
    val pronunciation: String = "",
    val studyCount: Int = 0,
    val isCorrectLast: Boolean = true,
    val lastStudiedAt: Long = 0,
)

@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String,
)

data class WebGroupRoundProgress(
    val groupId: Long,
    val minimumStudyCount: Int,
)

@Dao
interface WebWordDao {
    @Query("SELECT * FROM study_groups ORDER BY sortOrder ASC, createdAt DESC")
    fun observeGroups(): Flow<List<WebStudyGroup>>

    @Query("SELECT * FROM study_groups ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getGroups(): List<WebStudyGroup>

    @Query("SELECT * FROM study_groups WHERE id = :id")
    suspend fun getGroup(id: Long): WebStudyGroup?

    @Query("SELECT * FROM words WHERE groupId = :groupId ORDER BY id ASC")
    fun observeWords(groupId: Long): Flow<List<WebWord>>

    @Query("SELECT * FROM words WHERE groupId = :groupId ORDER BY id ASC")
    suspend fun getWords(groupId: Long): List<WebWord>

    @Query("SELECT groupId, MIN(studyCount) AS minimumStudyCount FROM words GROUP BY groupId")
    fun observeRounds(): Flow<List<WebGroupRoundProgress>>

    @Query("SELECT DISTINCT tag FROM words WHERE groupId = :groupId AND tag != '' ORDER BY tag")
    suspend fun getTags(groupId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: WebStudyGroup): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWords(words: List<WebWord>)

    @Update
    suspend fun updateGroup(group: WebStudyGroup)

    @Update
    suspend fun updateWord(word: WebWord)

    @Query(
        """UPDATE words
           SET studyCount = studyCount + 1,
               isCorrectLast = :isCorrect,
               lastStudiedAt = :studiedAt
           WHERE id = :wordId"""
    )
    suspend fun recordStudyResult(wordId: Long, isCorrect: Boolean, studiedAt: Long): Int

    @Query("DELETE FROM words WHERE groupId = :groupId")
    suspend fun deleteWords(groupId: Long)

    @Query("DELETE FROM study_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("UPDATE words SET studyCount = 0, isCorrectLast = 1, lastStudiedAt = 0 WHERE groupId = :groupId")
    suspend fun resetProgress(groupId: Long)

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: AppMeta)

    @Transaction
    suspend fun deleteGroupWithWords(groupId: Long) {
        deleteWords(groupId)
        deleteGroup(groupId)
    }
}

@Database(
    entities = [WebStudyGroup::class, WebWord::class, AppMeta::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(TangoWebDatabaseConstructor::class)
abstract class TangoWebDatabase : RoomDatabase() {
    abstract fun wordDao(): WebWordDao
}

expect object TangoWebDatabaseConstructor : RoomDatabaseConstructor<TangoWebDatabase> {
    override fun initialize(): TangoWebDatabase
}
