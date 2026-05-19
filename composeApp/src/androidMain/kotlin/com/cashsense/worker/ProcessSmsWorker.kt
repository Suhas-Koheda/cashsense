package com.cashsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashsense.GemmaAnalyzer
import com.cashsense.db.DriverFactory
import com.cashsense.model.TransactionData
import com.cashsense.db.CashSenseDb
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProcessSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val smsBody = inputData.getString("sms_body") ?: return Result.failure()
        val smsSender = inputData.getString("sms_sender") ?: ""

        val gemmaAnalyzer = GemmaAnalyzer(applicationContext)
        val transactionData = gemmaAnalyzer.analyzeSms(smsBody)

        val db = com.cashsense.db.DatabaseModule.getDatabase(DriverFactory(applicationContext))

        if (transactionData != null) {
            val entity = mapToEntity(transactionData, smsSender)
            db.cashSenseQueries.insertTransaction(entity)
        }
        return Result.success()
    }

    private fun mapToEntity(data: TransactionData, smsSender: String): com.cashsense.db.TransactionEntity {
        return com.cashsense.db.TransactionEntity(
            id = java.util.UUID.randomUUID().toString(),
            amount = data.amount,
            merchant = data.merchant,
            date = System.currentTimeMillis(),
            categoryId = data.category ?: "Others",
            notes = "Received via SMS Receiver",
            isDeleted = 0L,
            lastModified = System.currentTimeMillis(),
            needsReview = if ((data.confidence ?: 0.0) < 0.7) 1L else 0L,
            originalSmsText = smsSender
        )
    }

    private fun categorizeMerchant(merchant: String): String {
        return when (merchant.lowercase()) {
            "zomato", "swiggy", "dominos" -> "Food"
            "uber", "ola", "metro" -> "Transport"
            "hdfc", "icici", "sbi" -> "Banking"
            else -> "Uncategorized"
        }
    }

    private fun parseSmsFallback(smsBody: String): TransactionData? {
        val amountRegex = """(?:rs\.?|inr)\s*(\d+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(smsBody)?.groups?.get(1)?.value?.toDoubleOrNull()
        if (amountMatch != null) {
            return TransactionData(
                amount = amountMatch,
                currency = "INR",
                transaction_type = if (smsBody.contains("credit", ignoreCase = true)) "credit" else "debit",
                merchant = smsBody.split(" ").take(3).joinToString(" "),
                date = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                bank = null
            )
        }
        return null
    }
}
