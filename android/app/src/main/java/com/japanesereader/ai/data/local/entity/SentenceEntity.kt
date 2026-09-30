package com.japanesereader.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["article_id"])]
)
data class SentenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "translated_text") val translatedText: String,
    @ColumnInfo(name = "furigana_payload") val furiganaPayload: String,
    @ColumnInfo(name = "grammar_analysis") val grammarAnalysis: String,
    @ColumnInfo(name = "sequence_order") val sequenceOrder: Int,
    @ColumnInfo(name = "inspection_count") val inspectionCount: Int = 0,
    @ColumnInfo(name = "audio_play_count") val audioPlayCount: Int = 0,
    @ColumnInfo(name = "needs_deep_study") val needsDeepStudy: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
