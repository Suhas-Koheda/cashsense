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
    private val hdfcGeneric = Regex("(?i)rs\\.?\\s?([\\d,]+\\.?\\d*)\\s?debited")
    private val iciciPattern = Regex("(?i)acct.*?debited for (?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*).*?info[:\\s]+(.*?)\\.?\\s?available")
    private val iciciGeneric = Regex("(?i)inr\\s?([\\d,]+\\.?\\d*)\\s?debited")
    private val upiPattern = Regex("(?i)sent\\s?(?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*)\\s?to\\s(.*?)\\s?ur")
    private val sbiPattern = Regex("(?i)a/c.*?debited by (?:inr|rs)\\.?\\s?([\\d,]+\\.?\\d*)\\s?on.*?transfer to\\s(.*?)\\s?ref")
    
    private val gpayPattern = Regex("(?i)paid ₹?([\\d,\\.]+)\\s?to\\s(.+?)(?:\\s|$)")
    private val phonePePattern = Regex("(?i)sent ₹?([\\d,\\.]+)\\s?to\\s(.+?)(?:\\s|$)")

    fun extract(smsBody: String): ExtractedSmsData {
        // High confidence direct matches
        var match = hdfcPattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.95)

        match = iciciPattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.95)

        match = sbiPattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.90)

        match = upiPattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.90)
        
        match = gpayPattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.95)
        
        match = phonePePattern.find(smsBody)
        if (match != null) return buildData(match, isDebit = true, confidence = 0.95)

        // Fallback / Generic bank matches (No merchant captured)
        match = hdfcGeneric.find(smsBody)
        if (match != null) {
            val amt = parseAmount(match.groupValues.getOrNull(1))
            return ExtractedSmsData(amt, "Unknown Merchant", true, 0.7) // Fallback confidence
        }
        
        match = iciciGeneric.find(smsBody)
        if (match != null) {
            val amt = parseAmount(match.groupValues.getOrNull(1))
            return ExtractedSmsData(amt, "Unknown Merchant", true, 0.7)
        }

        // Generic fallback fuzzy matching
        val genericDebit = Regex("(?i)(?:debited|spent|paid|sent).*?(?:inr|rs|₹)\\.?\\s?([\\d,]+\\.?\\d*)").find(smsBody)
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
