package com.cashsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashsense.db.DriverFactory
import com.cashsense.db.CashSenseDb
import com.cashsense.sync.ScanStatus

class SmsScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = com.cashsense.db.DatabaseModule.getDatabase(DriverFactory(applicationContext))
        val scanner = SmsScanner(applicationContext, db)
        
        scanner.scanExistingSms { status ->
            ScanStatus.update(status)
        }
        ScanStatus.update(null)
        return Result.success()
    }
}
