package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "words",
    indices = [Index(value = ["groupId"])]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val english: String,
    val japanese: String,
    val tag: String = "",
    
    // Performance metrics
    val studyCount: Int = 0,
    val isCorrectLast: Boolean = true,
    val lastStudiedAt: Long = 0L
)
