package com.japanesereader.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_logs")
data class StudyLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sentence_id") val sentenceId: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
