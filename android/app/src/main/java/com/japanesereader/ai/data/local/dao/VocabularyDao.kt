package com.japanesereader.ai.data.local.dao

import androidx.room.*
import com.japanesereader.ai.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabularies ORDER BY created_at DESC")
    fun getAllVocabularies(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabularies WHERE mastery_status = :status ORDER BY created_at DESC")
    fun getVocabulariesByStatus(status: Int): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabularies WHERE id = :id LIMIT 1")
    suspend fun getVocabularyById(id: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: VocabularyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularies(vocabs: List<VocabularyEntity>)

    @Update
    suspend fun updateVocabulary(vocab: VocabularyEntity)

    @Query("DELETE FROM vocabularies WHERE id = :id")
    suspend fun deleteVocabularyById(id: String)
}
