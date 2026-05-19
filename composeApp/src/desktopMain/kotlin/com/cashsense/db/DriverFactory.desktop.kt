package com.cashsense.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DriverFactory actual constructor(private val context: Any?) {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:cashsense.db")
        try {
            CashSenseDb.Schema.create(driver)
        } catch (e: Exception) {
            // Already exists or other issue, ignore for now
        }
        return driver
    }
}
