package com.japanesereader.ai.data.repository

import com.google.gson.Gson
import com.japanesereader.ai.data.local.KomorebiDatabase
import com.japanesereader.ai.data.local.entity.*
import com.japanesereader.ai.data.remote.AnalyzeResponseDto
import com.japanesereader.ai.data.remote.KomorebiApiClient
import com.japanesereader.ai.data.remote.SyncPayloadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class KomorebiRepository(
    private val database: KomorebiDatabase,
    val prefs: com.japanesereader.ai.data.local.PreferencesManager? = null,
    val apiClient: KomorebiApiClient = KomorebiApiClient(prefs?.backendUrl ?: com.japanesereader.ai.data.local.PreferencesManager.DEFAULT_BACKEND_URL)
) {
    private val gson = Gson()

    val articles: Flow<List<ArticleEntity>> = database.articleDao().getAllArticles()
    val vocabularies: Flow<List<VocabularyEntity>> = database.vocabularyDao().getAllVocabularies()
    val settings: Flow<UserSettingsEntity?> = database.userSettingsDao().getUserSettingsFlow("usr_default")

    private val _syncStatus = MutableStateFlow("Synced")
    val syncStatus: StateFlow<String> = _syncStatus

    fun getSentencesForArticle(articleId: String): Flow<List<SentenceEntity>> {
        return database.sentenceDao().getSentencesByArticleId(articleId)
    }

    suspend fun analyzeText(text: String): AnalyzeResponseDto = withContext(Dispatchers.IO) {
        val apiKey = prefs?.deepseekApiKey?.ifBlank { null }
            ?: database.userSettingsDao().getUserSettings("usr_default")?.deepseekApiKey
        apiClient.analyzeText(text, apiKey)
    }
    suspend fun createArticle(title: String, category: String, rawText: String): ArticleEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val artId = "art_${now}"

        val apiKey = prefs?.deepseekApiKey?.ifBlank { null }
            ?: database.userSettingsDao().getUserSettings("usr_default")?.deepseekApiKey
        val analysis = apiClient.analyzeText(rawText, apiKey)

        val difficulty = analysis.difficulty_level
        val kanjiRatio = analysis.kanji_ratio
        val article = ArticleEntity(
            id = artId,
            userId = "usr_default",
            title = title,
            category = category,
            rawText = rawText,
            difficultyLevel = difficulty,
            kanjiRatio = kanjiRatio,
            createdAt = now,
            updatedAt = now
        )

        val sentenceEntities = if (analysis != null && analysis.sentences.isNotEmpty()) {
            analysis.sentences.mapIndexed { idx, s ->
                SentenceEntity(
                    id = "sent_${artId}_${idx + 1}",
                    articleId = artId,
                    originalText = s.original_text,
                    translatedText = s.translated_text,
                    furiganaPayload = gson.toJson(s.tokens),
                    grammarAnalysis = gson.toJson(s.grammar_points),
                    sequenceOrder = s.sequence_order,
                    inspectionCount = 0,
                    audioPlayCount = 0,
                    needsDeepStudy = false,
                    updatedAt = now
                )
            }
        } else {
            listOf(
                SentenceEntity(
                    id = "sent_${artId}_1",
                    articleId = artId,
                    originalText = rawText,
                    translatedText = "Terjemahan konteks: $title",
                    furiganaPayload = gson.toJson(listOf(mapOf("surface" to rawText, "reading" to rawText, "pos" to "noun"))),
                    grammarAnalysis = "[]",
                    sequenceOrder = 1,
                    inspectionCount = 0,
                    audioPlayCount = 0,
                    needsDeepStudy = false,
                    updatedAt = now
                )
            )
        }

        database.articleDao().insertArticle(article)
        database.sentenceDao().insertSentences(sentenceEntities)
        article
    }

    suspend fun deleteArticle(id: String) = withContext(Dispatchers.IO) {
        database.articleDao().deleteArticleById(id)
    }

    suspend fun addVocabulary(
        kanji: String,
        reading: String,
        meaning: String,
        pos: String = "noun",
        jlpt: String = "N5",
        sentenceId: String? = null
    ): VocabularyEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sentence = sentenceId?.let { database.sentenceDao().getSentenceById(it) }
        val needsDeepStudy = sentence?.needsDeepStudy ?: false
        val nextReview = com.japanesereader.ai.util.SrsEngine.initialInterval(needsDeepStudy, now)

        val vocab = VocabularyEntity(
            id = "voc_${now}",
            userId = "usr_default",
            sentenceId = sentenceId,
            kanji = kanji,
            reading = reading,
            meaning = meaning,
            partOfSpeech = pos,
            jlptLevel = jlpt,
            masteryStatus = 0,
            reviewCount = 0,
            nextReviewAt = nextReview,
            createdAt = now,
            updatedAt = now
        )
        database.vocabularyDao().insertVocabulary(vocab)
        vocab
    }

    suspend fun updateVocabulary(vocab: VocabularyEntity) = withContext(Dispatchers.IO) {
        database.vocabularyDao().updateVocabulary(vocab.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteVocabulary(id: String) = withContext(Dispatchers.IO) {
        database.vocabularyDao().deleteVocabularyById(id)
    }

    suspend fun recordStudyLog(sentenceId: String, actionType: String) = withContext(Dispatchers.IO) {
        try {
            val log = StudyLogEntity(
                id = "log_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}",
                userId = "usr_default",
                sentenceId = sentenceId,
                actionType = actionType,
                timestamp = System.currentTimeMillis()
            )
            database.studyLogDao().insertLog(log)
        } catch (e: Exception) {
            // Ignore telemetry failure
        }
    }

    suspend fun inspectSentence(sentenceId: String) = withContext(Dispatchers.IO) {
        database.sentenceDao().incrementInspectionCount(sentenceId)
        recordStudyLog(sentenceId, "INSPECT")
        apiClient.inspectSentence(sentenceId)
    }

    suspend fun recordAudioPlay(sentenceId: String) = withContext(Dispatchers.IO) {
        database.sentenceDao().incrementAudioPlayCount(sentenceId)
        recordStudyLog(sentenceId, "AUDIO_PLAY")
    }

    suspend fun updateSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        prefs?.let { p ->
            p.deepseekApiKey = settings.deepseekApiKey ?: ""
            p.furiganaMode = settings.furiganaMode
            p.kanjiFontStyle = settings.kanjiFontStyle
            p.ttsSpeed = settings.ttsSpeed
            p.deepseekTone = settings.deepseekTone
        }
        database.userSettingsDao().insertOrUpdateSettings(settings.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        _syncStatus.value = "Syncing..."
        try {
            val syncPayload = SyncPayloadDto(
                user_id = "usr_default",
                since_timestamp = 0L
            )
            val result = apiClient.pushSync(syncPayload)
            _syncStatus.value = if (result != null && result.success) "Synced" else "Offline"
        } catch (e: Exception) {
            _syncStatus.value = "Offline"
        }
    }
}
