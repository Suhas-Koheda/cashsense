package com.cashsense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun CashSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        CashSenseDarkColors
    } else {
        CashSenseLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
