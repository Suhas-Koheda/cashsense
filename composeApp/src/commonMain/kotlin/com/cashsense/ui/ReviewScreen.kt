package com.cashsense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cashsense.db.TransactionEntity
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(presenter: DashboardPresenter, onBack: () -> Unit) {
    val state by presenter.state.collectAsState()
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Needs Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.reviewTransactions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("All good! No transactions need review.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.reviewTransactions) { tx ->
                    Card(onClick = { editingTx = tx }) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text(tx.merchant, style = MaterialTheme.typography.titleMedium)
                            Text("Amount: ₹${tx.amount}", style = MaterialTheme.typography.bodyMedium)
                            Text("Extracted text: ${tx.originalSmsText ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                            Text("Click to edit/approve", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (editingTx != null) {
        EditTransactionDialog(
            tx = editingTx!!,
            onDismiss = { editingTx = null },
            onSave = { updatedTx ->
                presenter.updateTransaction(updatedTx)
                editingTx = null
            },
            onDelete = {
                presenter.deleteTransaction(editingTx!!.id)
                editingTx = null
            }
        )
    }
}
