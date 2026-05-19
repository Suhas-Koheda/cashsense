package com.cashsense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(presenter: DashboardPresenter, onBack: () -> Unit) {
    val state by presenter.state.collectAsState()
    var name by remember { mutableStateOf(state.userName) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    presenter.updateUserName(name)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Name")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("App Version: 1.0.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}
