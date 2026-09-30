package com.japanesereader.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "target_language") val targetLanguage: String = "id-ID",
    @ColumnInfo(name = "deepseek_tone") val deepseekTone: String = "colloquial",
    @ColumnInfo(name = "tts_speed") val ttsSpeed: Float = 1.0f,
    @ColumnInfo(name = "voice_model") val voiceModel: String = "ja_JP-hira-medium",
    @ColumnInfo(name = "furigana_mode") val furiganaMode: String = "always",
    @ColumnInfo(name = "dynamic_pastel_highlights") val dynamicPastelHighlights: Boolean = true,
    @ColumnInfo(name = "kanji_font_style") val kanjiFontStyle: String = "mincho",
    @ColumnInfo(name = "deepseek_api_key") val deepseekApiKey: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
