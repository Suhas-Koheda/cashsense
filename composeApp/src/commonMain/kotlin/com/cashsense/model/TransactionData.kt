package com.cashsense.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionData(
    val amount: Double,
    val currency: String,
    val transaction_type: String,
    val merchant: String,
    val date: String,
    val bank: String? = null,
    val category: String? = null,
    val confidence: Double = 1.0
)
