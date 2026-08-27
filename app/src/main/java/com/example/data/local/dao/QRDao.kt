package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BatchSessionEntity
import com.example.data.local.entity.QRHistoryEntity
import com.example.data.local.entity.QRPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QRDao {
    // History
    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QRHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: QRHistoryEntity): Long

    @Update
    suspend fun updateHistory(item: QRHistoryEntity)

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM qr_history")
    suspend fun clearAllHistory()

    @Query("SELECT COUNT(*) FROM qr_history")
    fun getHistoryCount(): Flow<Int>

    // Presets
    @Query("SELECT * FROM qr_presets ORDER BY timestamp DESC")
    fun getAllPresets(): Flow<List<QRPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: QRPresetEntity): Long

    @Update
    suspend fun updatePreset(preset: QRPresetEntity)

    @Query("DELETE FROM qr_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)

    @Query("DELETE FROM qr_presets")
    suspend fun clearAllPresets()

    // Batch sessions
    @Query("SELECT * FROM batch_sessions ORDER BY timestamp DESC")
    fun getAllBatchSessions(): Flow<List<BatchSessionEntity>>

    @Query("SELECT * FROM batch_sessions ORDER BY timestamp DESC LIMIT 20")
    fun getRecentBatchSessions(): Flow<List<BatchSessionEntity>>

    @Query("SELECT * FROM batch_sessions WHERE id = :id")
    suspend fun getBatchSessionById(id: Long): BatchSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchSession(session: BatchSessionEntity): Long

    @Update
    suspend fun updateBatchSession(session: BatchSessionEntity)

    @Query("DELETE FROM batch_sessions WHERE id = :id")
    suspend fun deleteBatchSessionById(id: Long)

    @Query("DELETE FROM batch_sessions")
    suspend fun clearAllBatchSessions()
}
