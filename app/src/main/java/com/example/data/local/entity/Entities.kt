package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_history")
data class QRHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val contentType: String,
    val styleJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String,
    val notes: String = ""
)

@Entity(tableName = "qr_presets")
data class QRPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val styleJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_sessions")
data class BatchSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val totalCount: Int,
    val successCount: Int = totalCount,
    val failureCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val zipFilePath: String = "",
    val itemsJson: String = "[]",
    val styleJson: String = "",
    val sourceType: String = "CSV",
    val notes: String = ""
)
