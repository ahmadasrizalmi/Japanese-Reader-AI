package com.japanesereader.ai.data.local.dao

import androidx.room.*
import com.japanesereader.ai.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE user_id = :userId LIMIT 1")
    fun getUserSettingsFlow(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE user_id = :userId LIMIT 1")
    suspend fun getUserSettings(userId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettingsEntity)
}
