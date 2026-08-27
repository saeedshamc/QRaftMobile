package com.example.data.local.repository

import com.example.data.local.dao.QRDao
import com.example.data.local.entity.BatchSessionEntity
import com.example.data.local.entity.QRHistoryEntity
import com.example.data.local.entity.QRPresetEntity
import kotlinx.coroutines.flow.Flow

class QRRepository(private val dao: QRDao) {
    val allHistory: Flow<List<QRHistoryEntity>> = dao.getAllHistory()
    val allPresets: Flow<List<QRPresetEntity>> = dao.getAllPresets()
    val recentBatches: Flow<List<BatchSessionEntity>> = dao.getRecentBatchSessions()
    val allBatches: Flow<List<BatchSessionEntity>> = dao.getAllBatchSessions()
    val historyCount: Flow<Int> = dao.getHistoryCount()

    suspend fun addHistory(item: QRHistoryEntity): Long = dao.insertHistory(item)
    suspend fun updateHistory(item: QRHistoryEntity) = dao.updateHistory(item)
    suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)
    suspend fun clearHistory() = dao.clearAllHistory()

    suspend fun addPreset(preset: QRPresetEntity): Long = dao.insertPreset(preset)
    suspend fun updatePreset(preset: QRPresetEntity) = dao.updatePreset(preset)
    suspend fun deletePreset(id: Long) = dao.deletePresetById(id)
    suspend fun clearPresets() = dao.clearAllPresets()

    suspend fun addBatchSession(session: BatchSessionEntity): Long = dao.insertBatchSession(session)
    suspend fun getBatchSession(id: Long): BatchSessionEntity? = dao.getBatchSessionById(id)
    suspend fun updateBatchSession(session: BatchSessionEntity) = dao.updateBatchSession(session)
    suspend fun deleteBatchSession(id: Long) = dao.deleteBatchSessionById(id)
    suspend fun clearBatchSessions() = dao.clearAllBatchSessions()
}
