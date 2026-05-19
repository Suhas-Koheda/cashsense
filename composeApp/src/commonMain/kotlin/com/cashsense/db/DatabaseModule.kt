package com.cashsense.db

import com.cashsense.db.CashSenseDb
import app.cash.sqldelight.db.SqlDriver

object DatabaseModule {
    private var instance: CashSenseDb? = null

    fun getDatabase(driverFactory: DriverFactory): CashSenseDb {
        if (instance == null) {
            val driver = driverFactory.createDriver()
            instance = CashSenseDb(driver)
            seedDatabaseIfEmpty(instance!!)
        }
        return instance!!
    }

    private fun seedDatabaseIfEmpty(db: CashSenseDb) {
        val categories = db.cashSenseQueries.selectAllCategories().executeAsList()
        if (categories.isEmpty()) {
            // Seed Categories
            val defaultCategories = listOf(
                CategoryEntity("Food", "Food", "Restaurant", "#FF5722", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Transport", "Transport", "DirectionsCar", "#FBC02D", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Shopping", "Shopping", "ShoppingBag", "#E91E63", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Bills", "Bills", "Receipt", "#2196F3", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Health", "Health", "MedicalServices", "#4CAF50", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Entertainment", "Entertainment", "Movie", "#9C27B0", 1L, System.currentTimeMillis(), 0L),
                CategoryEntity("Others", "Others", "MoreHoriz", "#9E9E9E", 1L, System.currentTimeMillis(), 0L)
            )
            defaultCategories.forEach { db.cashSenseQueries.insertCategory(it) }

            // Seed some Budgets
            val initialBudgets = listOf(
                BudgetEntity("b1", "Food", 3000.0, "05/2026", System.currentTimeMillis(), 0L),
                BudgetEntity("b2", "Transport", 1500.0, "05/2026", System.currentTimeMillis(), 0L),
                BudgetEntity("b3", "Shopping", 5000.0, "05/2026", System.currentTimeMillis(), 0L)
            )
            initialBudgets.forEach { db.cashSenseQueries.insertBudget(it) }

            // Seed a Recurring Payment (upcoming bill)
            val initialRecurring = listOf(
                RecurringPaymentEntity("r1", "Netflix Subscription", 199.0, "Monthly", 28L, "Entertainment", System.currentTimeMillis(), 0L)
            )
            initialRecurring.forEach { db.cashSenseQueries.insertRecurringPayment(it) }
        }
    }
}
