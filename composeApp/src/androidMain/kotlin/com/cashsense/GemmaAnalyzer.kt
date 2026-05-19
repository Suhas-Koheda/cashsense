package com.cashsense

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.cashsense.model.TransactionData
import com.cashsense.util.LogManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class GemmaAnalyzer(private val context: Context) {
    private val modelPath = "/data/local/tmp/llm/gemma-4-E2B-it-Q4_0.gguf"

    companion object {
        @Volatile
        private var instance: LlmInference? = null
        
        fun getInstance(context: Context, modelPath: String): LlmInference {
            return instance ?: synchronized(this) {
                instance ?: createInference(context, modelPath).also { instance = it }
            }
        }

        private fun createInference(context: Context, modelPath: String): LlmInference {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val availableMegs = memoryInfo.availMem / 1024L / 1024L
            
            LogManager.d("GemmaAnalyzer", "STEP 1: Starting MediaPipe initialization... (Available RAM: ${availableMegs}MB)")
            val file = java.io.File(modelPath)
            if (!file.exists()) {
                LogManager.e("GemmaAnalyzer", "CRITICAL: Model file NOT found at $modelPath")
            } else {
                LogManager.d("GemmaAnalyzer", "Model file found (${file.length() / 1024 / 1024} MB)")
            }

            return try {
                LogManager.d("GemmaAnalyzer", "STEP 2: Creating LlmInference from options (this may take 10-30s)...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .build()
                val result = LlmInference.createFromOptions(context, options)
                LogManager.d("GemmaAnalyzer", "STEP 3: LlmInference created successfully!")
                result
            } catch (e: Exception) {
                LogManager.e("GemmaAnalyzer", "FATAL: Failed to create LlmInference: ${e.message}")
                throw e
            }
        }
    }

    private val is64Bit = android.os.Process.is64Bit()

    private val tfliteAnalyzer: TfliteAnalyzer? by lazy {
        if (!is64Bit) TfliteAnalyzer(context) else null
    }

    private val inference: LlmInference? by lazy {
        if (is64Bit) {
            getInstance(context, modelPath)
        } else {
            null
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeSms(smsText: String): TransactionData? = withContext(Dispatchers.Default) {
        if (!is64Bit) {
            LogManager.d("GemmaAnalyzer", "Device is 32-bit. Using TFLite fallback.")
            return@withContext tfliteAnalyzer?.analyzeSms(smsText)
        }
        val currentInference = inference ?: return@withContext fallbackRegexExtract(smsText)
        LogManager.d("GemmaAnalyzer", "Analyzing SMS: $smsText")
        
        val prompt = """
            Extract transaction data from SMS. 
            Format: JSON only. 
            Example 1 (Debit): "Spent Rs.500 at Amazon" -> {"amount": 500.0, "currency": "INR", "transaction_type": "debit", "merchant": "Amazon", "date": "2024-07-27", "bank": "Unknown", "category": "Shopping", "confidence": 0.9}
            Example 2 (Promo): "Get 50% off on your next order" -> {"amount": 0.0, "currency": "INR", "transaction_type": "none", "merchant": "None", "date": "2024-07-27", "bank": "None", "category": "Others", "confidence": 0.0}
            
            Input SMS: "$smsText"
            JSON Output:
        """.trimIndent()

        val parsed = try {
            val response = currentInference.generateResponse(prompt)
            LogManager.d("GemmaAnalyzer", "AI raw response: $response")
            
            val jsonRegex = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
            val match = jsonRegex.find(response)
            val jsonStr = match?.value ?: response.replace("```json", "").replace("```", "").trim()
            
            LogManager.d("GemmaAnalyzer", "Cleaned JSON: $jsonStr")
            val data = json.decodeFromString<TransactionData>(jsonStr)
            
            if (data.confidence >= 0.7 && data.amount > 0) data else null
        } catch (e: Exception) {
            LogManager.e("GemmaAnalyzer", "Extraction error: ${e.message}")
            null
        }
        
        return@withContext parsed ?: fallbackRegexExtract(smsText)
    }

    private fun fallbackRegexExtract(smsText: String): TransactionData? {
        // Only fallback if we see very strong transaction indicators
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
            merchant = "Manual Review Required",
            date = dateStr,
            bank = "Unknown",
            category = "Others",
            confidence = 0.5
        )
    }

    suspend fun categorizeExpense(merchant: String, description: String = ""): String = withContext(Dispatchers.Default) {
        if (!is64Bit) {
            // Simple rule-based categorization as a quick fallback on 32-bit
            val lowerMerchant = merchant.lowercase()
            return@withContext when {
                lowerMerchant.contains("zomato") || lowerMerchant.contains("swiggy") || lowerMerchant.contains("dominos") || lowerMerchant.contains("food") || lowerMerchant.contains("restaurant") -> "Food"
                lowerMerchant.contains("uber") || lowerMerchant.contains("ola") || lowerMerchant.contains("metro") || lowerMerchant.contains("cab") || lowerMerchant.contains("taxi") -> "Transport"
                lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") || lowerMerchant.contains("myntra") || lowerMerchant.contains("shopping") -> "Shopping"
                lowerMerchant.contains("bill") || lowerMerchant.contains("electricity") || lowerMerchant.contains("water") || lowerMerchant.contains("recharge") -> "Bills"
                lowerMerchant.contains("hospital") || lowerMerchant.contains("pharmacy") || lowerMerchant.contains("medical") || lowerMerchant.contains("health") -> "Health"
                lowerMerchant.contains("netflix") || lowerMerchant.contains("spotify") || lowerMerchant.contains("movie") || lowerMerchant.contains("entertainment") -> "Entertainment"
                lowerMerchant.contains("salary") || lowerMerchant.contains("refund") || lowerMerchant.contains("interest") || lowerMerchant.contains("income") -> "Income"
                else -> "Others"
            }
        }
        val currentInference = inference ?: return@withContext "Others"
        val prompt = """
            Categorize this expense into EXACTLY ONE of: Food, Transport, Shopping, Bills, Health, Entertainment, Income, Others.
            Merchant: "$merchant"
            Description: "$description"
            Output ONLY the category name.
        """.trimIndent()

        return@withContext try {
            val resp = currentInference.generateResponse(prompt).trim()
            val valid = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Income", "Others")
            if (valid.any { it.equals(resp, ignoreCase = true) }) {
                valid.first { it.equals(resp, ignoreCase = true) }
            } else "Others"
        } catch (e: Exception) {
            "Others"
        }
    }
}