package com.japanesereader.ai.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class TokenDto(
    val surface: String,
    val reading: String,
    val romaji: String,
    val pos: String,
    val meaning: String? = null,
    val base_form: String? = null,
    val form: String? = null,
    val jlpt: String? = null
)

data class GrammarPointDto(
    val pattern: String,
    val explanation: String
)

data class SentenceAnalysisDto(
    val sequence_order: Int,
    val original_text: String,
    val translated_text: String,
    val tokens: List<TokenDto>,
    val grammar_points: List<GrammarPointDto>
)

data class AnalyzeResponseDto(
    val difficulty_level: String,
    val kanji_ratio: Double,
    val sentences: List<SentenceAnalysisDto>
)

data class InspectResponseDto(
    val success: Boolean,
    val inspection_count: Int,
    val needs_deep_study: Boolean
)

data class SyncPayloadDto(
    val user_id: String,
    val since_timestamp: Long,
    val articles: List<Map<String, Any>>? = null,
    val vocabularies: List<Map<String, Any>>? = null
)

data class SyncResponseDto(
    val success: Boolean,
    val timestamp: Long
)

data class TtsResponseDto(
    val url: String,
    val format: String = "audio/wav",
    val cached: Boolean = false
)

class KomorebiApiClient(
    private var baseUrl: String = "https://komorebi-reader-api.hannabi3108.workers.dev"
) {
    private val gson = Gson()

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun analyzeText(text: String, apiKey: String? = null): AnalyzeResponseDto = withContext(Dispatchers.IO) {
        // 1. If user supplied DeepSeek API key (BYOK), call DeepSeek directly
        if (!apiKey.isNullOrBlank() && !apiKey.startsWith("sk-optional")) {
            val deepSeekResult = com.japanesereader.ai.util.JapaneseMorphologyEngine.analyzeWithDeepSeek(text, apiKey)
            if (deepSeekResult != null) return@withContext deepSeekResult
        }

        // 2. Try Cloudflare Worker backend
        try {
            val endpoint = URL("$baseUrl/api/v1/analyze")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 6000
                readTimeout = 8000
            }

            val requestBody = mutableMapOf<String, Any>("text" to text)
            if (!apiKey.isNullOrBlank()) {
                requestBody["api_key"] = apiKey
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val response = reader.readText()
                    val parsed = gson.fromJson(response, AnalyzeResponseDto::class.java)
                    if (parsed != null && parsed.sentences.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore and fall through to local engine
        }

        // 3. Resilient native local morphology engine
        com.japanesereader.ai.util.JapaneseMorphologyEngine.analyzeLocal(text)
    }

    suspend fun inspectSentence(sentenceId: String): InspectResponseDto? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/v1/analytics/inspect")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }

            val body = mapOf("sentence_id" to sentenceId)
            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(body)) }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use {
                    return@withContext gson.fromJson(it.readText(), InspectResponseDto::class.java)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun synthesizeTts(
        text: String,
        speed: Float = 1.0f,
        voiceModel: String = "ja_JP-hira-medium"
    ): TtsResponseDto? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/v1/tts")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 5000
            }

            val payload = mapOf(
                "text" to text,
                "speed" to speed,
                "voice_model" to voiceModel
            )
            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(payload)) }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use {
                    return@withContext gson.fromJson(it.readText(), TtsResponseDto::class.java)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pushSync(payload: SyncPayloadDto): SyncResponseDto? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$baseUrl/api/v1/sync")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
            }

            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(payload)) }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use {
                    return@withContext gson.fromJson(it.readText(), SyncResponseDto::class.java)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
