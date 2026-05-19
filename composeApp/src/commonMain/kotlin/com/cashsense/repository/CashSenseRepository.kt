package com.cashsense.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CashSenseRepository(val database: CashSenseDb) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return database.cashSenseQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getTransactionsByMonth(start: Long, end: Long): Flow<List<TransactionEntity>> {
        return database.cashSenseQueries
            .selectByMonth(start, end)
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getAvailableMonths(): Flow<List<Pair<Int, Int>>> {
        return database.cashSenseQueries
            .getAvailableMonths()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list ->
                list.mapNotNull { 
                    val y = it.year?.toIntOrNull()
                    val m = it.month?.toIntOrNull()
                    if (y != null && m != null) Pair(y, m) else null
                }
            }
    }

    fun getTransactionsNeedingReview(): Flow<List<TransactionEntity>> {
        return database.cashSenseQueries
            .getTransactionsNeedingReview()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getAllCategories(): Flow<List<com.cashsense.db.CategoryEntity>> {
        return database.cashSenseQueries
            .selectAllCategories()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getAllBudgets(): Flow<List<com.cashsense.db.BudgetEntity>> {
        return database.cashSenseQueries
            .selectAllBudgets()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getAllRecurringPayments(): Flow<List<com.cashsense.db.RecurringPaymentEntity>> {
        return database.cashSenseQueries
            .selectAllRecurringPayments()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getSetting(key: String): Flow<String?> {
        return database.cashSenseQueries
            .getSetting(key)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
    }

    fun saveSetting(key: String, value: String) {
        database.cashSenseQueries.insertSetting(key, value)
    }

    fun addTransaction(transaction: TransactionEntity) {
        database.cashSenseQueries.insertTransaction(transaction)
    }

    fun updateTransaction(tx: TransactionEntity) {
        database.cashSenseQueries.updateTransaction(
            amount = tx.amount,
            merchant = tx.merchant,
            date = tx.date,
            categoryId = tx.categoryId,
            notes = tx.notes,
            lastModified = System.currentTimeMillis(),
            needsReview = tx.needsReview,
            id = tx.id
        )
    }

    fun deleteTransaction(id: String) {
        database.cashSenseQueries.deleteTransaction(System.currentTimeMillis(), id)
    }

    fun saveCategory(category: com.cashsense.db.CategoryEntity) {
        database.cashSenseQueries.insertCategory(category)
    }

    fun deleteCategory(id: String) {
        database.cashSenseQueries.deleteCategory(System.currentTimeMillis(), id)
        // Also soft-delete any budgets associated with this category
        database.cashSenseQueries.deleteBudgetByCategory(System.currentTimeMillis(), id)
    }

    fun saveBudget(budget: com.cashsense.db.BudgetEntity) {
        database.cashSenseQueries.insertBudget(budget)
    }

    fun updateBudget(id: String, newAmount: Double) {
        database.cashSenseQueries.updateBudget(newAmount, System.currentTimeMillis(), id)
    }

    fun deleteBudget(id: String) {
        database.cashSenseQueries.deleteBudget(System.currentTimeMillis(), id)
    }
}
