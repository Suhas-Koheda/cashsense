package com.cashsense.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.cashsense.db.TransactionEntity
import com.cashsense.ui.theme.*
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

    MaterialTheme(colorScheme = CashSenseLightColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = { 
                LargeFloatingActionButton(
                    onClick = { quickAddTemplate = null; showQuickAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
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
                    EditableQuickActionChips(presenter, onChipClick = { label, amount ->
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
                    if (state.isLoading) {
                        SkeletonTransactionList()
                    } else {
                        RecentTransactionsList(
                            transactions = state.transactions.take(20),
                            onTxClick = { editingTx = it }
                        )
                    }
                    
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
}

@Composable
fun EditableQuickActionChips(presenter: DashboardPresenter, onChipClick: (String, Double) -> Unit) {
    // A simplified editable chips system leveraging state
    var editChipIndex by remember { mutableStateOf<Int?>(null) }
    var chipLabelInput by remember { mutableStateOf("") }
    var chipAmountInput by remember { mutableStateOf("") }
    
    // In a real app we'd load these from a flow or Settings table via presenter
    // Hardcoded for demo state as requested:
    val initialChips = remember { mutableStateListOf(
        "Coffee" to 150.0,
        "Auto" to 100.0,
        "Food" to 300.0,
        "Grocery" to 500.0
    )}

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(initialChips.size) { index ->
            val chip = initialChips[index]
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onChipClick(chip.first, chip.second) },
                            onLongPress = { 
                                editChipIndex = index
                                chipLabelInput = chip.first
                                chipAmountInput = chip.second.toInt().toString()
                            }
                        )
                    },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shadowElevation = 2.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${chip.first} (₹${chip.second.toInt()})", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { 
                    editChipIndex = initialChips.size
                    chipLabelInput = ""
                    chipAmountInput = ""
                },
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, "Add custom chip", modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    if (editChipIndex != null) {
        AlertDialog(
            onDismissRequest = { editChipIndex = null },
            title = { Text(if (editChipIndex == initialChips.size) "Add Custom Chip" else "Edit Chip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chipLabelInput,
                        onValueChange = { chipLabelInput = it },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chipAmountInput,
                        onValueChange = { chipAmountInput = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = chipAmountInput.toDoubleOrNull() ?: 0.0
                    val lbl = chipLabelInput.ifBlank { "Custom" }
                    if (editChipIndex!! < initialChips.size) {
                        initialChips[editChipIndex!!] = lbl to amt
                    } else {
                        initialChips.add(lbl to amt)
                    }
                    // presenter.saveChipSetting(...) can be implemented here
                    editChipIndex = null
                }) { Text("Save") }
            },
            dismissButton = {
                if (editChipIndex!! < initialChips.size) {
                    TextButton(onClick = { initialChips.removeAt(editChipIndex!!); editChipIndex = null }, colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)) {
                        Text("Delete")
                    }
                } else {
                    TextButton(onClick = { editChipIndex = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun TopHeaderSection(state: DashboardState, presenter: DashboardPresenter, onReviewClick: () -> Unit) {
    var expandedMonth by remember { mutableStateOf(false) }
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    
    // Compute list of months from 2022 to Next Year
    val monthList = mutableListOf<Pair<Int, Int>>() // Year, Month
    for (y in (now.year + 1) downTo 2022) {
        for (m in 12 downTo 1) {
            if (y > now.year || (y == now.year && m <= now.monthNumber)) {
                monthList.add(y to m)
            }
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("CashSense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Box {
                TextButton(onClick = { expandedMonth = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(state.currentMonth, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    DropdownMenuItem(
                        text = { Text("All Time", fontWeight = FontWeight.Bold) },
                        onClick = {
                            presenter.setAllTime()
                            expandedMonth = false
                        }
                    )
                    Divider()
                    monthList.forEach { (y, m) ->
                        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        DropdownMenuItem(
                            text = { Text("${monthNames[m-1]} $y") },
                            onClick = {
                                presenter.setMonth(y, m)
                                expandedMonth = false
                            }
                        )
                    }
                }
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.reviewTransactions.isNotEmpty()) {
                BadgedBox(badge = { Badge(containerColor = ErrorRed) { Text("${state.reviewTransactions.size}") } }) {
                    IconButton(onClick = onReviewClick) {
                        Icon(Icons.Default.Warning, contentDescription = "Review", tint = ErrorRed)
                    }
                }
            }
            IconButton(onClick = { presenter.triggerSync() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onBackground)
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
        Spacer(modifier = Modifier.height(16.dp))
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
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(32.dp)),
        shadowElevation = 8.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = listOf(PrimaryPurple, PrimaryPurpleDark)))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text("Remaining Balance", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                        Text("₹${(budget - spent).toInt()}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("Spent: ₹${spent.toInt()} of ₹${budget.toInt()}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))
                }
            }
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
    Card(onClick = onClick, modifier = Modifier.width(150.dp), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Text("$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(budget.categoryId, style = MaterialTheme.typography.labelMedium, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text("₹${spent.toInt()}/₹${budget.amount.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ExpenseBreakdownSection(breakdown: Map<String, Double>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Expense Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (breakdown.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No expenses yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    breakdown.entries.sortedByDescending { it.value }.take(5).forEach { (cat, amount) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
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
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🍃", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("It's quiet here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No transactions found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            transactions.forEach { tx ->
                Card(onClick = { onTxClick(tx) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(tx.categoryId.lowercase()) {
                                "food" -> Icons.Default.Fastfood
                                "transport" -> Icons.Default.DirectionsCar
                                "shopping" -> Icons.Default.ShoppingCart
                                "banking" -> Icons.Default.AccountBalance
                                else -> Icons.Default.AttachMoney
                            }
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(tx.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val dateObj = Instant.fromEpochMilliseconds(tx.date).toLocalDateTime(TimeZone.currentSystemDefault())
                                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                Text("${monthNames[dateObj.monthNumber-1]} ${dateObj.dayOfMonth}, ${dateObj.year}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("${if(tx.amount > 0) "+" else "-"} ₹${kotlin.math.abs(tx.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if(tx.amount > 0) SuccessGreen else NeutralText)
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonTransactionList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(5) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).shimmerEffect())
        }
    }
}

@Composable
fun ProcessingStatusCard(status: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition()
    val startOffsetX by transition.animateFloat(initialValue = -2 * size.width.toFloat(), targetValue = 2 * size.width.toFloat(), animationSpec = infiniteRepeatable(animation = tween(1500)))
    this.onGloballyPositioned { size = it.size }.background(brush = Brush.linearGradient(colors = listOf(Color.LightGray.copy(alpha=0.3f), Color.LightGray.copy(alpha=0.6f), Color.LightGray.copy(alpha=0.3f)), start = Offset(startOffsetX, 0f), end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())))
}
