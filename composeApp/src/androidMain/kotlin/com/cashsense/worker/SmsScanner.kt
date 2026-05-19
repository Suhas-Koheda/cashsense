package com.cashsense.worker

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.cashsense.GemmaAnalyzer
import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import com.cashsense.util.LogManager

class SmsScanner(private val context: Context, private val database: CashSenseDb) {
    private val transactionKeywords = listOf("debited", "credited", "spent", "paid", "transaction", "txn", "vpa", "upi")
    
    suspend fun scanExistingSms(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        LogManager.d("SmsScanner", "Warming up Gemma AI model (this may take time)...")
        val analyzer = GemmaAnalyzer(context)
        
        val existingTxIds = database.cashSenseQueries.selectAll().executeAsList()
            .mapNotNull { it.originalSmsText }
            .toSet()

        val seedSmsList = listOf(
            MockSms("mock_1", "HDFCBank", "Your ACCT ending in 4321 has been debited with INR 450.00 for Starbucks Coffee on 2026-05-18. Info: UPI-Starbucks."),
            MockSms("mock_2", "SBI-UPI", "Dear Customer, txn of Rs. 1,200.00 spent on Uber Ride on 2026-05-18. UPI Ref: 123456789."),
            MockSms("mock_3", "ICICIBk", "Alert: INR 2,500.00 paid from Acct ending 9876 to Nike Store on 2026-05-17. Ref: UPI."),
            MockSms("mock_4", "Paytm", "Spent Rs.950.00 at Pizza Hut on 2026-05-16. Order ref: PH-9821. Powered by UPI."),
            MockSms("mock_5", "AMEX", "Your Card ending 1001 charged INR 150.00 at Metro Ticket on 2026-05-15. Txn successful.")
        )

        // Process seed messages first (if not already processed)
        val unprocessedSeeds = seedSmsList.filter { it.id !in existingTxIds }
        if (unprocessedSeeds.isNotEmpty()) {
            LogManager.d("SmsScanner", "Processing ${unprocessedSeeds.size} unprocessed seed SMS messages...")
            unprocessedSeeds.forEachIndexed { index, sms ->
                onProgress("Analyzing seed SMS ${index + 1}/${unprocessedSeeds.size}...")
                analyzeAndInsert(sms.id, sms.body, sms.sender, analyzer)
            }
        }

        // Process real SMS inbox
        val uri = Uri.parse("content://sms/inbox")
        val cursor = try {
            context.contentResolver.query(
                uri, 
                arrayOf("_id", "address", "body", "date"), 
                null, 
                null, 
                "date DESC LIMIT 50"
            )
        } catch (e: Exception) {
            LogManager.e("SmsScanner", "Failed to query system SMS inbox: ${e.message}")
            null
        }

        cursor?.use {
            val idIndex = it.getColumnIndex("_id")
            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            
            val total = it.count
            LogManager.d("SmsScanner", "Found $total potential messages. Starting sequential analysis...")
            var processed = 0
            
            while (it.moveToNext()) {
                processed++
                val smsId = it.getString(idIndex)
                val body = it.getString(bodyIndex)
                val sender = it.getString(addressIndex)

                if (smsId in existingTxIds) continue

                // Only process if it looks like a bank transaction
                if (sender.length >= 6 && transactionKeywords.any { body.contains(it, ignoreCase = true) }) {
                    onProgress("Analyzing transaction $processed/$total...")
                    analyzeAndInsert(smsId, body, sender, analyzer)
                }
            }
        }
        onProgress("Scan complete!")
        LogManager.d("SmsScanner", "Scan finished successfully.")
    }

    private suspend fun analyzeAndInsert(smsId: String, body: String, sender: String, analyzer: GemmaAnalyzer) {
        LogManager.d("SmsScanner", "Analyzing SMS from $sender...")
        val txData = analyzer.analyzeSms(body)
        if (txData != null) {
            LogManager.d("SmsScanner", "SUCCESS: Extracted ₹${txData.amount} from ${txData.merchant}")
            val entity = TransactionEntity(
                id = java.util.UUID.randomUUID().toString(),
                amount = txData.amount,
                merchant = txData.merchant,
                date = System.currentTimeMillis(),
                categoryId = txData.category ?: "Others",
                notes = "Auto-extracted from SMS",
                isDeleted = 0L,
                lastModified = System.currentTimeMillis(),
                needsReview = if (txData.confidence < 0.7) 1L else 0L,
                originalSmsText = body
            )
            database.cashSenseQueries.insertTransaction(entity)
        } else {
            LogManager.d("SmsScanner", "Skipped: No transaction found in this message.")
        }
    }

    private data class MockSms(val id: String, val sender: String, val body: String)
}
