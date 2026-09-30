package com.japanesereader.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocabularies")
data class VocabularyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sentence_id") val sentenceId: String? = null,
    @ColumnInfo(name = "kanji") val kanji: String,
    @ColumnInfo(name = "reading") val reading: String,
    @ColumnInfo(name = "meaning") val meaning: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "jlpt_level") val jlptLevel: String,
    @ColumnInfo(name = "mastery_status") val masteryStatus: Int = 0, // 0: Baru, 1: Dipelajari, 2: Dikuasai
    @ColumnInfo(name = "review_count") val reviewCount: Int = 0,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
