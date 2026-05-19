package com.cashsense.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.cashsense.db.TransactionEntity
import kotlinx.datetime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(presenter: DashboardPresenter) {
    val state by presenter.state.collectAsState()
    

    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState()
    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddTemplate by remember { mutableStateOf<Pair<String, Double>?>(null) }
    
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }
    var showCategoryBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<com.cashsense.db.BudgetEntity?>(null) }
    
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        floatingActionButton = { 
            LargeFloatingActionButton(
                onClick = { quickAddTemplate = null; showQuickAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction", modifier = Modifier.size(30.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                TopHeaderSection(
                    state = state, 
                    presenter = presenter, 
                    onReviewClick = { presenter.navigateTo(Screen.Review) }
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                FinancialSummaryCard(
                    spent = state.totalSpent,
                    budget = state.monthlyBudget,
                    onClick = {
                        budgetInput = state.monthlyBudget.toInt().toString()
                        showBudgetDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                QuickActionChips(onChipClick = { label, amount ->
                    quickAddTemplate = label to amount
                    showQuickAdd = true
                })
                
                if (state.processingStatus != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ProcessingStatusCard(state.processingStatus!!)
                }

                Spacer(modifier = Modifier.height(16.dp))
                BudgetAtAGlanceSection(
                    budgets = state.budgets,
                    breakdown = state.categoryBreakdown,
                    onAddBudgetClick = { showCategoryBudgetDialog = true; editingBudget = null },
                    onEditBudget = { b -> editingBudget = b; showCategoryBudgetDialog = true }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                ExpenseBreakdownSection(state.categoryBreakdown)
                
                SectionHeader("Recent Activity", onAction = {})
                RecentTransactionsList(
                    transactions = state.transactions.take(20),
                    onTxClick = { editingTx = it }
                )
                
                Spacer(modifier = Modifier.height(100.dp))
            }

            if (showQuickAdd) {
                ModalBottomSheet(
                    onDismissRequest = { showQuickAdd = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    QuickAddContent(
                        template = quickAddTemplate,
                        onDismiss = { showQuickAdd = false },
                        onSave = { amt, mcht, cat, isD -> 
                            presenter.addTransaction(amt, mcht, cat, isD)
                            showQuickAdd = false 
                        }
                    )
                }
            }

            if (showBudgetDialog) {
                AlertDialog(
                    onDismissRequest = { showBudgetDialog = false },
                    title = { Text("Set Monthly Budget") },
                    text = {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text("Total Budget Amount") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val amt = budgetInput.toDoubleOrNull()
                            if (amt != null && amt > 0) presenter.updateMonthlyBudget(amt)
                            showBudgetDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") } }
                )
            }

            if (showCategoryBudgetDialog) {
                var selectedCategory by remember { mutableStateOf(editingBudget?.categoryId ?: "Food") }
                var categoryBudgetInput by remember { mutableStateOf(editingBudget?.amount?.toInt()?.toString() ?: "") }
                val categoriesList = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Others")
                var expanded by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showCategoryBudgetDialog = false },
                    title = { Text(if (editingBudget == null) "Set Category Budget" else "Edit Category Budget") },
                    text = {
                        Column {
                            if (editingBudget == null) {
                                Box {
                                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(selectedCategory)
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        categoriesList.forEach { cat ->
                                            DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; expanded = false })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            OutlinedTextField(
                                value = categoryBudgetInput,
                                onValueChange = { categoryBudgetInput = it },
                                label = { Text("Limit Amount") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val amt = categoryBudgetInput.toDoubleOrNull()
                            if (amt != null && amt > 0) {
                                if (editingBudget != null) {
                                    presenter.updateCategoryBudget(editingBudget!!.id, amt)
                                } else {
                                    presenter.saveCategoryBudget(selectedCategory, amt)
                                }
                            }
                            showCategoryBudgetDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            if (editingBudget != null) {
                                TextButton(onClick = { presenter.deleteCategoryBudget(editingBudget!!.id); showCategoryBudgetDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text("Delete")
                                }
                            }
                            TextButton(onClick = { showCategoryBudgetDialog = false }) { Text("Cancel") }
                        }
                    }
                )
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
    }
}

@Composable
fun TopHeaderSection(state: DashboardState, presenter: DashboardPresenter, onReviewClick: () -> Unit) {
    var expandedMonth by remember { mutableStateOf(false) }
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("CashSense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box {
                TextButton(onClick = { expandedMonth = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(state.currentMonth, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    for (m in 1..12) {
                        DropdownMenuItem(
                            text = { Text("${m.toString().padStart(2, '0')}/${now.year}") },
                            onClick = {
                                presenter.setMonth(now.year, m)
                                expandedMonth = false
                            }
                        )
                    }
                }
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.reviewTransactions.isNotEmpty()) {
                BadgedBox(badge = { Badge { Text("${state.reviewTransactions.size}") } }) {
                    IconButton(onClick = onReviewClick) {
                        Icon(Icons.Default.Warning, contentDescription = "Review", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = { presenter.triggerSync() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync")
            }
        }
    }
}

@Composable
fun QuickAddContent(template: Pair<String, Double>?, onDismiss: () -> Unit, onSave: (Double, String, String, Boolean) -> Unit) {
    var amount by remember { mutableStateOf(template?.second?.toString() ?: "") }
    var merchant by remember { mutableStateOf(template?.first ?: "") }
    var categoryId by remember { mutableStateOf(template?.first ?: "Others") }
    var isDebit by remember { mutableStateOf(true) }
    
    Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
        Text("Quick Add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row {
            FilterChip(selected = isDebit, onClick = { isDebit = true }, label = { Text("Expense") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = !isDebit, onClick = { isDebit = false }, label = { Text("Income") })
        }
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            prefix = { Text("₹") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt != null && merchant.isNotBlank()) {
                    onSave(amt, merchant, categoryId, isDebit)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save Transaction")
        }
    }
}

@Composable
fun SectionHeader(title: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        TextButton(onClick = onAction) { Text("See all", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun FinancialSummaryCard(spent: Double, budget: Double, onClick: () -> Unit) {
    val progress = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(32.dp)).shimmerEffect(),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text("Total Balance (Monthly)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                        Text("₹${(budget - spent).toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("Monthly Spend: ₹${spent.toInt()} / ₹${budget.toInt()}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun QuickActionChips(onChipClick: (String, Double) -> Unit) {
    val actions = listOf("Coffee" to 150.0, "Auto" to 100.0, "Food" to 300.0, "Grocery" to 500.0)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(actions) { (label, amt) ->
            FilterChip(
                selected = false,
                onClick = { onChipClick(label, amt) },
                label = { Text("$label (₹${amt.toInt()})") },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun BudgetAtAGlanceSection(budgets: List<com.cashsense.db.BudgetEntity>, breakdown: Map<String, Double>, onAddBudgetClick: () -> Unit, onEditBudget: (com.cashsense.db.BudgetEntity) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onAddBudgetClick) { Icon(Icons.Default.Add, "Add Budget") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        if (budgets.isEmpty()) {
            Text("No budgets set for this month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(budgets) { budget ->
                    val spent = breakdown[budget.categoryId] ?: 0.0
                    BudgetCard(budget, spent, onClick = { onEditBudget(budget) })
                }
            }
        }
    }
}

@Composable
fun BudgetCard(budget: com.cashsense.db.BudgetEntity, spent: Double, onClick: () -> Unit) {
    val progress = if (budget.amount > 0) (spent / budget.amount).coerceIn(0.0, 1.0).toFloat() else 0f
    val pct = (progress * 100).toInt()
    Card(onClick = onClick, modifier = Modifier.width(140.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(60.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                Text("$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(budget.categoryId, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text("₹${spent.toInt()}/₹${budget.amount.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ExpenseBreakdownSection(breakdown: Map<String, Double>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Expense Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (breakdown.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No data for this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    breakdown.entries.sortedByDescending { it.value }.take(5).forEach { (cat, amount) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(cat, style = MaterialTheme.typography.bodyMedium)
                            Text("₹${amount.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTransactionsList(transactions: List<TransactionEntity>, onTxClick: (TransactionEntity) -> Unit) {
    if (transactions.isEmpty()) {
        Text("No transactions found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            transactions.forEach { tx ->
                Card(onClick = { onTxClick(tx) }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(tx.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val dateStr = Instant.fromEpochMilliseconds(tx.date).toString().split("T").first()
                            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${if(tx.amount > 0) "+" else "-"} ₹${kotlin.math.abs(tx.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if(tx.amount > 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingStatusCard(status: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.width(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition()
    val startOffsetX by transition.animateFloat(initialValue = -2 * size.width.toFloat(), targetValue = 2 * size.width.toFloat(), animationSpec = infiniteRepeatable(animation = tween(1500)))
    this.onGloballyPositioned { size = it.size }.background(brush = Brush.linearGradient(colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), start = Offset(startOffsetX, 0f), end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())))
}
