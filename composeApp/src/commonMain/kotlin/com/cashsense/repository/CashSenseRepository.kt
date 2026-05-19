package com.cashsense.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashsense.db.CashSenseDb
import com.cashsense.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class CashSenseRepository(val database: CashSenseDb) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return database.cashSenseQueries
            .selectAll()
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

    fun deleteBudget(id: String) {
        database.cashSenseQueries.deleteBudget(System.currentTimeMillis(), id)
    }
}
