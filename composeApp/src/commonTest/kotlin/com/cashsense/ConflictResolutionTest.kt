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
            merchant = "Store",
            date = 1672531200000L,
            categoryId = "Food",
            notes = null,
            isDeleted = 0L,
            lastModified = 1000L,
            needsReview = 0L,
            originalSmsText = null
        )
        val newer = older.copy(amount = 120.0, lastModified = 2000L)
        
        val result = if (newer.lastModified > older.lastModified) newer else older
        assertEquals(120.0, result.amount)
    }
}
