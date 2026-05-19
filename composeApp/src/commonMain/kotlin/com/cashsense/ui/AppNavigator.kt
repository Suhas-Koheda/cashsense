package com.cashsense.ui

import androidx.compose.animation.*
import androidx.compose.runtime.*

@Composable
fun AppNavigator(presenter: DashboardPresenter) {
    val state by presenter.state.collectAsState()
    
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
