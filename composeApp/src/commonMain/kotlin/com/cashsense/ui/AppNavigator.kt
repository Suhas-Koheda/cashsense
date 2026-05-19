package com.cashsense.ui

import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.cashsense.ui.theme.CashSenseTheme

@Composable
fun AppNavigator(presenter: DashboardPresenter) {
    val state by presenter.state.collectAsState()
    
    val darkTheme = when (state.themePreference) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    
    CashSenseTheme(darkTheme = darkTheme) {
        AnimatedContent(
            targetState = state.currentScreen,
            transitionSpec = {
                if (targetState == Screen.Dashboard) {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                } else {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                }
            }
        ) { screen ->
            when (screen) {
                Screen.Dashboard -> DashboardScreen(presenter)
                Screen.Settings -> SettingsScreen(presenter, onBack = { presenter.navigateTo(Screen.Dashboard) })
                Screen.Logs -> LogsScreen(onBack = { presenter.navigateTo(Screen.Dashboard) })
                Screen.Review -> ReviewScreen(presenter, onBack = { presenter.navigateTo(Screen.Dashboard) })
            }
        }
    }
}
