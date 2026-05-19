package com.cashsense.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory actual constructor(private val context: Any?) {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        return AndroidSqliteDriver(CashSenseDb.Schema, context as Context, "cashsense.db")
    }
}
