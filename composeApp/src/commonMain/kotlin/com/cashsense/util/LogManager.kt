package com.cashsense.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String
)

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(level: String, tag: String, message: String) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = "${now.hour}:${now.minute}:${now.second}"
        val entry = LogEntry(timestamp, level, tag, message)
        
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 500) currentList.removeAt(currentList.size - 1)
        _logs.value = currentList
        
        // Also print to system console/logcat
        println("[$level] $tag: $message")
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)
}
