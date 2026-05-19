package com.cashsense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cashsense.db.TransactionEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun EditTransactionDialog(
    tx: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    onDelete: () -> Unit
) {
    var amountStr by remember { mutableStateOf(kotlin.math.abs(tx.amount).toString()) }
    var merchant by remember { mutableStateOf(tx.merchant) }
    var categoryId by remember { mutableStateOf(tx.categoryId) }
    var isDebit by remember { mutableStateOf(tx.amount < 0 || tx.notes?.contains("debit", ignoreCase = true) == true) }

    val categoriesList = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Others")
    var expandedCat by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(selected = isDebit, onClick = { isDebit = true }, label = { Text("Expense") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isDebit, onClick = { isDebit = false }, label = { Text("Income") })
                }
                
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Category")
                Box {
                    OutlinedButton(onClick = { expandedCat = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(categoryId)
                    }
                    DropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                        categoriesList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { categoryId = cat; expandedCat = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                onSave(tx.copy(
                    amount = if (isDebit) -amt else amt,
                    merchant = merchant,
                    categoryId = categoryId
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
