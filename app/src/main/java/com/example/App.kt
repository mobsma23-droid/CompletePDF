package com.example

import android.app.Application
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("CrashHandler", "FATAL EXCEPTION in thread '${thread.name}': ${throwable.message}", throwable)
            } catch (e: Exception) {
                // Ignore secondary logging failures
            }
            // Pass to default handler for clean OS lifecycle handling
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
