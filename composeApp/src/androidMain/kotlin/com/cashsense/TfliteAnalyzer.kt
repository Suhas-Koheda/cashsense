package com.cashsense

import android.content.Context
import com.cashsense.model.TransactionData
import com.cashsense.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class TfliteAnalyzer(private val context: Context) {
    private val modelPath = "/data/local/tmp/llm/sms_extractor.tflite"
    private var interpreter: Interpreter? = null

    init {
        try {
            val file = File(modelPath)
            if (file.exists()) {
                interpreter = Interpreter(file)
                LogManager.d("TfliteAnalyzer", "Successfully initialized TFLite interpreter with $modelPath")
            } else {
                LogManager.i("TfliteAnalyzer", "TFLite model file not found at $modelPath. Will use regex fallback.")
            }
        } catch (e: Throwable) {
            LogManager.e("TfliteAnalyzer", "Failed to initialize TFLite interpreter: ${e.message}")
        }
    }

    suspend fun analyzeSms(smsText: String): TransactionData? = withContext(Dispatchers.Default) {
        val currentInterpreter = interpreter
        if (currentInterpreter == null) {
            LogManager.d("TfliteAnalyzer", "No TFLite interpreter, running regex fallback.")
            return@withContext fallbackRegexExtract(smsText)
        }

        try {
            // Suppose the TFLite model expects a fixed size input vector (e.g. 256 floats/tokens)
            // and outputs transaction attributes: [is_transaction, amount, is_debit, confidence]
            val inputVal = preprocessSms(smsText)
            val outputVal = Array(1) { FloatArray(4) } // Output shape: [1, 4] -> [is_transaction, amount, is_debit, confidence]
            
            currentInterpreter.run(inputVal, outputVal)
            
            val isTransaction = outputVal[0][0] > 0.5f
            val amount = outputVal[0][1].toDouble()
            val isDebit = outputVal[0][2] > 0.5f
            val confidence = outputVal[0][3].toDouble()

            if (isTransaction && amount > 0.0) {
                val type = if (isDebit) "debit" else "credit"
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                LogManager.d("TfliteAnalyzer", "TFLite inference success: Amount=$amount, Type=$type, Confidence=$confidence")
                return@withContext TransactionData(
                    amount = amount,
                    currency = "INR",
                    transaction_type = type,
                    merchant = "TFLite Extracted Merchant",
                    date = dateStr,
                    bank = "Unknown",
                    category = "Others",
                    confidence = confidence
                )
            }
        } catch (e: Throwable) {
            LogManager.e("TfliteAnalyzer", "Error during TFLite inference: ${e.message}")
        }

        return@withContext fallbackRegexExtract(smsText)
    }

    private fun preprocessSms(smsText: String): FloatArray {
        // Simple bag-of-words or basic character hashing vectorizer for demonstration / TFLite input compatibility
        val input = FloatArray(256)
        for (i in 0 until minOf(smsText.length, 256)) {
            input[i] = smsText[i].code.toFloat() / 255f
        }
        return input
    }

    private fun fallbackRegexExtract(smsText: String): TransactionData? {
        val isTransaction = smsText.contains("debited", ignoreCase = true) || 
                           smsText.contains("credited", ignoreCase = true) ||
                           smsText.contains("txn", ignoreCase = true) ||
                           smsText.contains("spent", ignoreCase = true)

        if (!isTransaction) return null

        val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)")
        val amountMatcher = amountPattern.matcher(smsText)
        var amount = 0.0
        if (amountMatcher.find()) {
            amount = amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        }
        
        if (amount == 0.0) return null

        val isDebit = smsText.contains("debited", ignoreCase = true) || 
                      smsText.contains("spent", ignoreCase = true) || 
                      smsText.contains("paid", ignoreCase = true)
        val type = if (isDebit) "debit" else "credit"
        
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        return TransactionData(
            amount = amount,
            currency = "INR",
            transaction_type = type,
            merchant = "Manual Review Required (32-bit Fallback)",
            date = dateStr,
            bank = "Unknown",
            category = "Others",
            confidence = 0.5
        )
    }
}
