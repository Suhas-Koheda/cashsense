package com.cashsense.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ScanStatus {
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    fun update(status: String?) {
        _progress.value = status
    }
}
