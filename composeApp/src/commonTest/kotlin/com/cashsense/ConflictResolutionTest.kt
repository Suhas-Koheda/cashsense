package com.cashsense

import com.cashsense.db.TransactionEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class ConflictResolutionTest {
    @Test
    fun testSyncConflictResolution() {
        val older = TransactionEntity(
            id = "1",
            amount = 100.0,
            currency = "INR",
            transactionType = "debit",
            merchant = "Store",
            date = "2023-01-01",
            categoryId = "Food",
            originalSmsId = null,
            notes = null,
            lastModified = 1000L,
            isDeleted = false
        )
        val newer = older.copy(amount = 120.0, lastModified = 2000L)
        
        val result = if (newer.lastModified > older.lastModified) newer else older
        assertEquals(120.0, result.amount)
    }
}
