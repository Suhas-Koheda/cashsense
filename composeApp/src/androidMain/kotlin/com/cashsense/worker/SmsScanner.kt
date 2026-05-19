package com.cashsense.worker

import android.content.Context
import android.net.Uri
import com.cashsense.GemmaAnalyzer
import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import com.cashsense.router.FastPathExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cashsense.util.LogManager
import java.util.UUID

class SmsScanner(private val context: Context, private val database: CashSenseDb) {
    private val transactionKeywords = listOf("debited", "credited", "spent", "paid", "transaction", "txn", "vpa", "upi")
    
    suspend fun scanExistingSms(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val existingTxIds = database.cashSenseQueries.selectAll().executeAsList()
            .mapNotNull { it.originalSmsText }
            .toSet()

        val extractor = FastPathExtractor()
        var gemmaAnalyzer: GemmaAnalyzer? = null // Lazy init

        val uri = Uri.parse("content://sms/inbox")
        val cursor = try {
            context.contentResolver.query(
                uri, 
                arrayOf("_id", "address", "body", "date"), 
                null, 
                null, 
                "date DESC" // Removed LIMIT 50 to scan all SMS
            )
        } catch (e: Exception) {
            LogManager.e("SmsScanner", "Failed to query system SMS inbox: ${e.message}")
            null
        }

        cursor?.use {
            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            
            val total = it.count
            LogManager.d("SmsScanner", "Found $total potential messages. Starting sequential analysis...")
            var processed = 0
            
            while (it.moveToNext()) {
                processed++
                val body = it.getString(bodyIndex)
                val sender = it.getString(addressIndex)

                if (body in existingTxIds) continue

                if (sender.length >= 6 && transactionKeywords.any { kw -> body.contains(kw, ignoreCase = true) }) {
                    onProgress("Analyzing transaction $processed/$total...")
                    
                    val fastResult = extractor.extract(body)
                    if (fastResult.confidence >= 0.7 && fastResult.amount != null && fastResult.merchant != null) {
                        LogManager.d("SmsScanner", "FAST SUCCESS: Extracted ₹${fastResult.amount} from ${fastResult.merchant}")
                        insertTx(fastResult.amount, fastResult.merchant, "Others", fastResult.isDebit, body, 0L)
                    } else {
                        // Fallback to Gemma
                        if (gemmaAnalyzer == null) {
                            onProgress("Warming up AI for unrecognized format...")
                            gemmaAnalyzer = GemmaAnalyzer(context)
                        }
                        val gemmaResult = gemmaAnalyzer?.analyzeSms(body)
                        if (gemmaResult != null) {
                            insertTx(
                                gemmaResult.amount, 
                                gemmaResult.merchant, 
                                gemmaResult.category ?: "Others", 
                                gemmaResult.transaction_type == "debit", 
                                body, 
                                if (gemmaResult.confidence < 0.7) 1L else 0L
                            )
                        } else {
                            // Needs manual review
                            insertTx(0.0, "Unknown", "Others", true, body, 1L)
                        }
                    }
                }
            }
        }
        onProgress("Scan complete!")
        LogManager.d("SmsScanner", "Scan finished successfully.")
    }

    private fun insertTx(amount: Double, merchant: String, category: String, isDebit: Boolean, body: String, needsReview: Long) {
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = if (isDebit) -amount else amount,
            merchant = merchant,
            date = System.currentTimeMillis(),
            categoryId = category,
            notes = "Auto-extracted from SMS",
            isDeleted = 0L,
            lastModified = System.currentTimeMillis(),
            needsReview = needsReview,
            originalSmsText = body
        )
        database.cashSenseQueries.insertTransaction(entity)
    }
}
