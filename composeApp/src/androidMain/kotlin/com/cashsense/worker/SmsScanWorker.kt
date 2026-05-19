package com.cashsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashsense.db.DriverFactory
import com.cashsense.sync.ScanStatus
import com.cashsense.util.LogManager

class SmsScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LogManager.d("SmsScanWorker", "Starting SMS scanning work in background...")
        try {
            val db = com.cashsense.db.DatabaseModule.getDatabase(DriverFactory(applicationContext))
            val scanner = SmsScanner(applicationContext, db)
            
            scanner.scanExistingSms { status ->
                ScanStatus.update(status)
            }
            ScanStatus.update(null)
            LogManager.d("SmsScanWorker", "SMS scanning work completed successfully.")
            return Result.success()
        } catch (e: Exception) {
            LogManager.e("SmsScanWorker", "Error scanning SMS in background: ${e.message}")
            ScanStatus.update(null)
            if (runAttemptCount < 3) {
                LogManager.d("SmsScanWorker", "Retrying SMS scan...")
                return Result.retry()
            }
            return Result.failure()
        }
    }
}
