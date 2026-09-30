package com.japanesereader.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.japanesereader.ai.data.local.dao.*
import com.japanesereader.ai.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ArticleEntity::class,
        SentenceEntity::class,
        VocabularyEntity::class,
        StudyLogEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KomorebiDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: KomorebiDatabase? = null

        fun getInstance(context: Context): KomorebiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KomorebiDatabase::class.java,
                    "komorebi_reader.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial data
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        seedInitialDatabase(database)
                    }
                }
            }
        }

        private suspend fun seedInitialDatabase(db: KomorebiDatabase) {
            val now = System.currentTimeMillis()
            val userId = "usr_default"

            // 1. Initial Articles
            val art1 = ArticleEntity(
                id = "art_tanaka_line",
                userId = userId,
                title = "Pesan LINE Tanaka",
                category = "Percakapan",
                rawText = "今週末、渋谷のカフェで話しませんか？",
                difficultyLevel = "N4",
                kanjiRatio = 0.38,
                createdAt = now - 7200000L,
                updatedAt = now - 7200000L
            )

            val sent1 = SentenceEntity(
                id = "sent_tanaka_01",
                articleId = "art_tanaka_line",
                originalText = "今週末、渋谷のカフェで話しませんか？",
                translatedText = "Maukah kamu berbincang di kafe Shibuya akhir pekan ini?",
                furiganaPayload = """[
                    {"surface":"今週末","reading":"こんしゅうまつ","romaji":"konshuumatsu","pos":"noun","jlpt":"N4","meaning":"Akhir pekan ini"},
                    {"surface":"、","reading":"","romaji":"","pos":"punct","jlpt":"-"},
                    {"surface":"渋谷","reading":"しぶや","romaji":"shibuya","pos":"noun","jlpt":"N4","meaning":"Shibuya"},
                    {"surface":"の","reading":"の","romaji":"no","pos":"particle","jlpt":"N5","meaning":"Partikel asosiasi"},
                    {"surface":"カフェ","reading":"カフェ","romaji":"kafe","pos":"noun","jlpt":"N5","meaning":"Kafe"},
                    {"surface":"で","reading":"で","romaji":"de","pos":"particle","jlpt":"N5","meaning":"Partikel lokasi"},
                    {"surface":"話しませんか","reading":"はなしませんか","romaji":"hanashimasenka","pos":"verb","jlpt":"N5","meaning":"Maukah mengobrol?"},
                    {"surface":"？","reading":"","romaji":"","pos":"punct","jlpt":"-"}
                ]""",
                grammarAnalysis = """[{"pattern":"〜ませんか","explanation":"Pola ajakan sopan untuk mengajak lawan bicara."}]""",
                sequenceOrder = 1,
                inspectionCount = 2,
                audioPlayCount = 1,
                needsDeepStudy = false,
                updatedAt = now - 7200000L
            )

            val art2 = ArticleEntity(
                id = "art_konbini_ningen",
                userId = userId,
                title = "Kutipan Konbini Ningen",
                category = "Buku & Artikel",
                rawText = "普通の人という架空の生き物を演じる。",
                difficultyLevel = "N3",
                kanjiRatio = 0.52,
                createdAt = now - 86400000L,
                updatedAt = now - 86400000L
            )

            val sent2 = SentenceEntity(
                id = "sent_konbini_01",
                articleId = "art_konbini_ningen",
                originalText = "普通の人という架空の生き物を演じる。",
                translatedText = "Memerankan sosok makhluk imajiner yang dinamakan manusia normal.",
                furiganaPayload = """[
                    {"surface":"普通","reading":"ふつう","romaji":"futsuu","pos":"noun","jlpt":"N4","meaning":"Biasa / normal"},
                    {"surface":"の","reading":"の","romaji":"no","pos":"particle","jlpt":"N5","meaning":"Partikel kepemilikan"},
                    {"surface":"人間","reading":"にんげん","romaji":"ningen","pos":"noun","jlpt":"N3","meaning":"Manusia"},
                    {"surface":"という","reading":"という","romaji":"toiu","pos":"particle","jlpt":"N4","meaning":"Yang dinamakan"},
                    {"surface":"架空","reading":"かくう","romaji":"kakuu","pos":"noun","jlpt":"N1","meaning":"Fiktif / imajiner"},
                    {"surface":"の","reading":"の","romaji":"no","pos":"particle","jlpt":"N5","meaning":"Partikel kepemilikan"},
                    {"surface":"生き物","reading":"いきもの","romaji":"ikimono","pos":"noun","jlpt":"N3","meaning":"Makhluk hidup"},
                    {"surface":"を","reading":"を","romaji":"wo","pos":"particle","jlpt":"N5","meaning":"Partikel objek"},
                    {"surface":"演じる","reading":"えんじる","romaji":"enjiru","pos":"verb","jlpt":"N2","meaning":"Memerankan"},
                    {"surface":"。","reading":"","romaji":"","pos":"punct","jlpt":"-"}
                ]""",
                grammarAnalysis = """[{"pattern":"〜という","explanation":"Menyatakan sebutan atau definisi konsep."}]""",
                sequenceOrder = 1,
                inspectionCount = 1,
                audioPlayCount = 1,
                needsDeepStudy = false,
                updatedAt = now - 86400000L
            )

            db.articleDao().insertArticle(art1)
            db.articleDao().insertArticle(art2)
            db.sentenceDao().insertSentence(sent1)
            db.sentenceDao().insertSentence(sent2)

            // 2. Initial Vocabularies
            val voc1 = VocabularyEntity(
                id = "voc_01",
                userId = userId,
                sentenceId = sent1.id,
                kanji = "今週末",
                reading = "こんしゅうまつ",
                meaning = "Akhir pekan ini",
                partOfSpeech = "noun",
                jlptLevel = "N4",
                masteryStatus = 1,
                reviewCount = 2,
                nextReviewAt = now + 86400000L,
                createdAt = now - 7200000L,
                updatedAt = now - 7200000L
            )

            val voc2 = VocabularyEntity(
                id = "voc_02",
                userId = userId,
                sentenceId = sent1.id,
                kanji = "咲く",
                reading = "さく",
                meaning = "Mekar (bunga)",
                partOfSpeech = "verb",
                jlptLevel = "N4",
                masteryStatus = 2,
                reviewCount = 5,
                nextReviewAt = now + 500000000L,
                createdAt = now - 90000000L,
                updatedAt = now - 90000000L
            )

            db.vocabularyDao().insertVocabulary(voc1)
            db.vocabularyDao().insertVocabulary(voc2)

            // 3. Initial Settings
            val initialSettings = UserSettingsEntity(
                userId = userId,
                targetLanguage = "id-ID",
                deepseekTone = "colloquial",
                ttsSpeed = 1.0f,
                voiceModel = "ja_JP-hira-medium",
                furiganaMode = "always",
                dynamicPastelHighlights = true,
                kanjiFontStyle = "mincho",
                deepseekApiKey = null,
                updatedAt = now
            )
            db.userSettingsDao().insertOrUpdateSettings(initialSettings)
        }
    }
}
