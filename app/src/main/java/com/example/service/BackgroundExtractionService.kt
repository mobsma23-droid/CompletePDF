package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service for robust background processing of PDF AI extraction,
 * Excel/CSV compilation, and Google Drive uploads without process suspension.
 */
class BackgroundExtractionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "pdf_extractor_channel"
        const val CHANNEL_NAME = "PDF Product Extractor"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_COMPLETE = "com.example.service.ACTION_COMPLETE"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_MAX_PROGRESS = "extra_max_progress"
        const val EXTRA_INDETERMINATE = "extra_indeterminate"

        fun startService(
            context: Context,
            title: String = "Processing PDFs in Background",
            text: String = "Starting extraction...",
            progress: Int = 0,
            maxProgress: Int = 100
        ) {
            val intent = Intent(context, BackgroundExtractionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX_PROGRESS, maxProgress)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("BackgroundService", "Failed to start foreground service (${e.javaClass.simpleName}): ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            title: String,
            text: String,
            progress: Int,
            maxProgress: Int = 100,
            indeterminate: Boolean = false
        ) {
            val intent = Intent(context, BackgroundExtractionService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX_PROGRESS, maxProgress)
                putExtra(EXTRA_INDETERMINATE, indeterminate)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("BackgroundService", "Failed to update foreground service: ${e.message}")
            }
        }

        fun notifyCompleted(context: Context, title: String, text: String) {
            val intent = Intent(context, BackgroundExtractionService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("BackgroundService", "Failed to complete foreground service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundExtractionService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("BackgroundService", "Failed to stop service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        try {
            when (action) {
                ACTION_START -> {
                    val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Processing PDFs"
                    val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Extracting catalog data..."
                    val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                    val max = intent?.getIntExtra(EXTRA_MAX_PROGRESS, 100) ?: 100

                    val notification = buildNotification(title, text, progress, max, false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }

                ACTION_UPDATE -> {
                    val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Processing PDFs"
                    val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Extracting catalog data..."
                    val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                    val max = intent?.getIntExtra(EXTRA_MAX_PROGRESS, 100) ?: 100
                    val indeterminate = intent?.getBooleanExtra(EXTRA_INDETERMINATE, false) ?: false

                    val notification = buildNotification(title, text, progress, max, indeterminate)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(NOTIFICATION_ID, notification)
                }

                ACTION_COMPLETE -> {
                    val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Processing Complete"
                    val text = intent?.getStringExtra(EXTRA_TEXT) ?: "All PDF files successfully extracted & exported."

                    showCompletionNotification(title, text)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                ACTION_STOP -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error during onStartCommand action $action: ${e.message}", e)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PDFExtractor::BackgroundProcessingWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30 mins max timeout safety
            }
        } catch (e: Exception) {
            Log.e("BackgroundService", "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress during background PDF analysis, table parsing and cloud sync."
                    setShowBadge(false)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e("BackgroundService", "Failed to create notification channel: ${e.message}")
            }
        }
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletionNotification(title: String, text: String) {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(COMPLETION_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to show completion notification: ${e.message}")
        }
    }
}
