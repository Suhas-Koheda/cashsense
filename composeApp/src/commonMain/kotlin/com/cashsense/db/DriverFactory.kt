package com.cashsense.db

expect class DriverFactory(context: Any? = null) {
    fun createDriver(): app.cash.sqldelight.db.SqlDriver
}
