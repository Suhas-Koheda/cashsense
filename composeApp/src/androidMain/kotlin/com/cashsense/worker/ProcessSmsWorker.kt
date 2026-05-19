package com.cashsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashsense.GemmaAnalyzer
import com.cashsense.db.DriverFactory
import com.cashsense.db.DatabaseModule
import com.cashsense.db.TransactionEntity
import com.cashsense.router.FastPathExtractor
import java.util.UUID

class ProcessSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val smsBody = inputData.getString("sms_body") ?: return Result.failure()
        val smsSender = inputData.getString("sms_sender") ?: ""

        val db = DatabaseModule.getDatabase(DriverFactory(applicationContext))
        val extractor = FastPathExtractor()
        
        val fastResult = extractor.extract(smsBody)
        
        if (fastResult.confidence >= 0.7 && fastResult.amount != null && fastResult.merchant != null) {
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
        } else {
            // Fallback to Gemma
            val gemmaAnalyzer = GemmaAnalyzer(applicationContext)
            val gemmaResult = gemmaAnalyzer.analyzeSms(smsBody)
            if (gemmaResult != null) {
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
            }
        }
        
        return Result.success()
    }

    private fun categorizeMerchant(merchant: String): String {
        return when (merchant.lowercase()) {
            "zomato", "swiggy", "dominos" -> "Food"
            "uber", "ola", "metro" -> "Transport"
            "hdfc", "icici", "sbi" -> "Banking"
            else -> "Others"
        }
    }
}
