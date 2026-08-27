package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_files")
data class ProcessedFileEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileSizeByte: Long,
    val totalPages: Int,
    val totalChunks: Int,
    val productCount: Int,
    val stageLabel: String,
    val xlsxFilePath: String?,
    val csvFilePath: String?,
    val driveXlsxUrl: String?,
    val driveCsvUrl: String?,
    val errorMessage: String?,
    val timestampMs: Long = System.currentTimeMillis()
)
