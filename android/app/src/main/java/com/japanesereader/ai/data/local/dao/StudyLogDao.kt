package com.japanesereader.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.japanesereader.ai.data.local.entity.StudyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StudyLogEntity)

    @Query("SELECT * FROM study_logs WHERE sentence_id = :sentenceId ORDER BY timestamp DESC")
    fun getLogsBySentenceId(sentenceId: String): Flow<List<StudyLogEntity>>

    @Query("SELECT * FROM study_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<StudyLogEntity>>

    @Query("SELECT COUNT(*) FROM study_logs WHERE action_type = :actionType")
    fun getCountByActionType(actionType: String): Flow<Int>
}
