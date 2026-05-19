package com.cashsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashsense.GemmaAnalyzer
import com.cashsense.db.DriverFactory
import com.cashsense.db.DatabaseModule
import com.cashsense.db.TransactionEntity
import com.cashsense.router.FastPathExtractor
import com.cashsense.util.LogManager
import java.util.UUID

class ProcessSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val smsBody = inputData.getString("sms_body") ?: return Result.failure()
        val smsSender = inputData.getString("sms_sender") ?: ""

        LogManager.d("ProcessSmsWorker", "Processing background SMS from $smsSender")

        val db = DatabaseModule.getDatabase(DriverFactory(applicationContext))
        val extractor = FastPathExtractor()
        
        val fastResult = extractor.extract(smsBody)
        
        if (fastResult.confidence >= 0.7 && fastResult.amount != null && fastResult.merchant != null) {
            LogManager.d("ProcessSmsWorker", "FastPath success: ${fastResult.amount} from ${fastResult.merchant}")
            val entity = TransactionEntity(
                id = UUID.randomUUID().toString(),
                amount = if (fastResult.isDebit) -fastResult.amount else fastResult.amount,
                merchant = fastResult.merchant,
                date = System.currentTimeMillis(),
                categoryId = categorizeMerchant(fastResult.merchant),
                notes = "Auto-extracted via Regex",
                isDeleted = 0L,
                lastModified = System.currentTimeMillis(),
                needsReview = 0L,
                originalSmsText = smsBody
            )
            db.cashSenseQueries.insertTransaction(entity)
        } else if (fastResult.amount != null || fastResult.confidence > 0.0) {
            LogManager.d("ProcessSmsWorker", "Low confidence, falling back to Gemma AI")
            val gemmaAnalyzer = GemmaAnalyzer(applicationContext)
            val gemmaResult = gemmaAnalyzer.analyzeSms(smsBody)
            if (gemmaResult != null) {
                LogManager.d("ProcessSmsWorker", "Gemma success: ${gemmaResult.amount} from ${gemmaResult.merchant}")
                val entity = TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    amount = if (gemmaResult.transaction_type == "debit") -gemmaResult.amount else gemmaResult.amount,
                    merchant = gemmaResult.merchant,
                    date = System.currentTimeMillis(),
                    categoryId = gemmaResult.category ?: "Others",
                    notes = "Extracted via AI",
                    isDeleted = 0L,
                    lastModified = System.currentTimeMillis(),
                    needsReview = if (gemmaResult.confidence < 0.7) 1L else 0L,
                    originalSmsText = smsBody
                )
                db.cashSenseQueries.insertTransaction(entity)
            } else {
                LogManager.d("ProcessSmsWorker", "Gemma failed, saving for manual review")
                val entity = TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    amount = 0.0,
                    merchant = "Unknown",
                    date = System.currentTimeMillis(),
                    categoryId = "Others",
                    notes = "Needs manual review",
                    isDeleted = 0L,
                    lastModified = System.currentTimeMillis(),
                    needsReview = 1L,
                    originalSmsText = smsBody
                )
                db.cashSenseQueries.insertTransaction(entity)
            }
        } else {
            LogManager.d("ProcessSmsWorker", "Skipped SMS from $smsSender")
        }
        
        return Result.success()
    }

    private fun categorizeMerchant(merchant: String): String {
        return when (merchant.lowercase()) {
            "zomato", "swiggy", "dominos" -> "Food"
            "uber", "ola", "metro" -> "Transport"
            "hdfc", "icici", "sbi", "axis" -> "Banking"
            else -> "Others"
        }
    }
}
