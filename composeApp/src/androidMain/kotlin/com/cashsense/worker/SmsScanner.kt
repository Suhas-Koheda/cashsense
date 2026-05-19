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
    private val transactionKeywords = listOf(
        "debited", "credited", "spent", "paid", "transaction", "txn", "vpa", "upi",
        "inr", "rs", "₹", "hdfc", "icici", "sbi", "axis", "gpay", "phonepe", "paytm", 
        "bank", "acct", "account"
    )
    
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
                "date DESC"
            )
        } catch (e: Exception) {
            LogManager.e("SmsScanner", "Failed to query system SMS inbox: ${e.message}")
            null
        }

        if (cursor == null) {
            LogManager.e("SmsScanner", "Cursor is null, likely missing READ_SMS permission.")
            onProgress("Error: Cannot read SMS.")
            return@withContext
        }

        var processed = 0
        var skipped = 0
        var saved = 0

        cursor.use {
            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            
            val total = it.count
            LogManager.d("SmsScanner", "Found $total potential messages in inbox.")
            
            while (it.moveToNext()) {
                processed++
                val body = it.getString(bodyIndex) ?: ""
                val sender = it.getString(addressIndex) ?: ""

                if (body in existingTxIds) {
                    skipped++
                    continue
                }

                if (sender.isNotBlank() && transactionKeywords.any { kw -> body.contains(kw, ignoreCase = true) }) {
                    LogManager.d("SmsScanner", "Checking SMS from $sender: ${body.take(50)}...")
                    
                    val fastResult = extractor.extract(body)
                    if (fastResult.confidence >= 0.7 && fastResult.amount != null && fastResult.merchant != null) {
                        LogManager.d("SmsScanner", "FAST SUCCESS: Extracted ₹${fastResult.amount} from ${fastResult.merchant}")
                        insertTx(fastResult.amount, fastResult.merchant, "Others", fastResult.isDebit, body, 0L)
                        saved++
                        onProgress("Synced $saved transactions...")
                    } else if (fastResult.amount != null || fastResult.confidence > 0.0) {
                        // Might be a valid transaction, but confidence is low. Fallback to Gemma
                        LogManager.d("SmsScanner", "Low confidence (${fastResult.confidence}) for message, falling back to AI.")
                        onProgress("Analyzing complex message...")
                        
                        if (gemmaAnalyzer == null) {
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
                            saved++
                            onProgress("Synced $saved transactions...")
                        } else {
                            insertTx(0.0, "Unknown", "Others", true, body, 1L) // Needs manual review
                            saved++
                            onProgress("Synced $saved transactions...")
                        }
                    } else {
                        LogManager.d("SmsScanner", "Skipped SMS from $sender (Not recognized as transaction)")
                        skipped++
                    }
                } else {
                    skipped++
                }
            }
            LogManager.d("SmsScanner", "Scan finished: Processed=$processed, Saved=$saved, Skipped=$skipped")
        }
        onProgress("Scan complete! Synced $saved transactions.")
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
