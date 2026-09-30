package com.japanesereader.ai.data.local.dao

import androidx.room.*
import com.japanesereader.ai.data.local.entity.SentenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE article_id = :articleId ORDER BY sequence_order ASC")
    fun getSentencesByArticleId(articleId: String): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE article_id = :articleId ORDER BY sequence_order ASC")
    suspend fun getSentencesByArticleIdSync(articleId: String): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE id = :id LIMIT 1")
    suspend fun getSentenceById(id: String): SentenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentence(sentence: SentenceEntity)

    @Update
    suspend fun updateSentence(sentence: SentenceEntity)

    @Query("UPDATE sentences SET inspection_count = inspection_count + 1, needs_deep_study = CASE WHEN inspection_count + 1 >= 3 THEN 1 ELSE 0 END, updated_at = :now WHERE id = :id")
    suspend fun incrementInspectionCount(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sentences WHERE article_id = :articleId")
    suspend fun deleteSentencesByArticleId(articleId: String)
}
