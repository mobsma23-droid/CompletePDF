package com.example.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * WorkManager CoroutineWorker for reliable, resilient background extraction tasks.
 * Provides foreground notification binding, surviving app minimize/close, screen lock, and system pressure.
 */
class ExtractionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "pdf_extraction_work"
        const val KEY_TOTAL_TASKS = "key_total_tasks"
        const val KEY_COMPLETED_TASKS = "key_completed_tasks"
        const val KEY_CURRENT_FILE = "key_current_file"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val totalTasks = inputData.getInt(KEY_TOTAL_TASKS, 1)
        val currentFileName = inputData.getString(KEY_CURRENT_FILE) ?: "PDF Flyer"

        try {
            // Set as foreground work with notification
            val initialForegroundInfo = createForegroundInfo(
                title = "Processing PDF Flyer",
                text = "Extracting data from $currentFileName...",
                progress = 0,
                maxProgress = 100
            )
            setForeground(initialForegroundInfo)

            // Update worker progress
            setProgress(
                workDataOf(
                    KEY_COMPLETED_TASKS to 0,
                    KEY_TOTAL_TASKS to totalTasks,
                    KEY_CURRENT_FILE to currentFileName
                )
            )

            Log.d("ExtractionWorker", "WorkManager task initialized for $currentFileName")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ExtractionWorker", "ExtractionWorker error: ${e.message}", e)
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")))
        }
    }

    private fun createForegroundInfo(
        title: String,
        text: String,
        progress: Int,
        maxProgress: Int
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, BackgroundExtractionService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
