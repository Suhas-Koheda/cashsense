package com.cashsense.router

/**
 * Result from parsing an SMS.
 * If confidence is >= 0.8, it's safe to save directly.
 * If confidence is 0.5 - 0.79, it might need Gemma review.
 * If confidence < 0.5, we should flag for user review or pass to Gemma.
 */
data class ExtractedSmsData(
    val amount: Double?,
    val merchant: String?,
    val isDebit: Boolean,
    val confidence: Double
)

class FastPathExtractor {
    
    // Patterns for Indian Banks & UPI
    private val hdfcPattern = Regex("(?i)rs\\.?\\s?([\\d,]+\\.?\\d*)\\s?has been debited from a/c.*?to\\s(.*?)\\s?on")
    private val iciciPattern = Regex("(?i)acct.*?debited for (?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*).*?info[:\\s]+(.*?)\\.?\\s?available")
    private val upiPattern = Regex("(?i)sent\\s?(?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*)\\s?to\\s(.*?)\\s?ur")
    private val sbiPattern = Regex("(?i)a/c.*?debited by (?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*)\\s?on.*?transfer to\\s(.*?)\\s?ref")

    fun extract(smsBody: String): ExtractedSmsData {
        // Fast paths - Regex matching (< 5ms)
        var match = hdfcPattern.find(smsBody)
        if (match != null) {
            return buildData(match, isDebit = true, confidence = 0.95)
        }

        match = iciciPattern.find(smsBody)
        if (match != null) {
            return buildData(match, isDebit = true, confidence = 0.95)
        }

        match = upiPattern.find(smsBody)
        if (match != null) {
            return buildData(match, isDebit = true, confidence = 0.90) // UPI merchants can be ambiguous
        }
        
        match = sbiPattern.find(smsBody)
        if (match != null) {
            return buildData(match, isDebit = true, confidence = 0.90)
        }

        // Add additional fallback/fuzzy matching here if needed (returns lower confidence)
        val genericDebit = Regex("(?i)(?:debited|spent|paid).*?(?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*)").find(smsBody)
        if (genericDebit != null) {
            val amount = parseAmount(genericDebit.groupValues[1])
            return ExtractedSmsData(amount, "Unknown Merchant", true, 0.4) // Needs Gemma review
        }

        return ExtractedSmsData(null, null, true, 0.0) // Completely unrecognized
    }

    private fun buildData(match: MatchResult, isDebit: Boolean, confidence: Double): ExtractedSmsData {
        val amountStr = match.groupValues.getOrNull(1)
        val merchantStr = match.groupValues.getOrNull(2)?.trim()
        
        val amount = parseAmount(amountStr)
        
        // Slightly penalize confidence if merchant string looks weird/too long
        var finalConfidence = confidence
        if (merchantStr != null && merchantStr.length > 30) {
            finalConfidence -= 0.2
        }
        
        return ExtractedSmsData(amount, merchantStr, isDebit, finalConfidence)
    }

    private fun parseAmount(amountStr: String?): Double? {
        return amountStr?.replace(",", "")?.toDoubleOrNull()
    }
}
