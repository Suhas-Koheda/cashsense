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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
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
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }
    var showCategoryBudgetDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = { 
            LargeFloatingActionButton(
                onClick = { showQuickAdd = true },
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
                
                // Inline TopBar Actions alongside the Header/Branding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderSection(state.userName)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { presenter.navigateTo(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = { presenter.navigateTo(Screen.Logs) }) {
                            Icon(Icons.Default.List, contentDescription = "Logs")
                        }
                        Box {
                            IconButton(onClick = presenter::triggerSync) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                            }
                            if (state.lastSyncTime == null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Clicking on the Summary Card allows updating the monthly budget
                FinancialSummaryCard(
                    spent = state.totalSpent,
                    budget = state.monthlyBudget,
                    onClick = {
                        budgetInput = state.monthlyBudget.toInt().toString()
                        showBudgetDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                QuickActionChips()
                
                if (state.processingStatus != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ProcessingStatusCard(state.processingStatus!!)
                }

                Spacer(modifier = Modifier.height(16.dp))
                BudgetAtAGlanceSection(
                    budgets = state.budgets,
                    breakdown = state.categoryBreakdown,
                    onAddBudgetClick = { showCategoryBudgetDialog = true }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                ExpenseBreakdownSection(state.categoryBreakdown)
                
                SectionHeader("Recent Activity", onAction = {})
                RecentTransactionsCarousel(state.transactions.take(8))
                
                if (state.recurringPayments.isNotEmpty()) {
                    SectionHeader("Upcoming Bills", onAction = {})
                    UpcomingBillsSection(state.recurringPayments)
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }

            if (showQuickAdd) {
                ModalBottomSheet(
                    onDismissRequest = { showQuickAdd = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    QuickAddSheet { showQuickAdd = false }
                }
            }

            // Dialog for setting Monthly Budget
            if (showBudgetDialog) {
                AlertDialog(
                    onDismissRequest = { showBudgetDialog = false },
                    title = { Text("Set Monthly Budget") },
                    text = {
                        Column {
                            Text("Enter your total monthly spending limit:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("Budget Amount") },
                                prefix = { Text("₹") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = budgetInput.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    presenter.updateMonthlyBudget(amount)
                                    showBudgetDialog = false
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBudgetDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Dialog for setting Category Budget
            if (showCategoryBudgetDialog) {
                var selectedCategory by remember { mutableStateOf("Food") }
                var categoryBudgetInput by remember { mutableStateOf("") }
                val categoriesList = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Others")
                var expanded by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showCategoryBudgetDialog = false },
                    title = { Text("Set Category Budget") },
                    text = {
                        Column {
                            Text("Select Category:")
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(selectedCategory)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    categoriesList.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                selectedCategory = cat
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Enter spending limit for $selectedCategory:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = categoryBudgetInput,
                                onValueChange = { categoryBudgetInput = it },
                                label = { Text("Limit Amount") },
                                prefix = { Text("₹") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = categoryBudgetInput.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    presenter.saveCategoryBudget(selectedCategory, amount)
                                    showCategoryBudgetDialog = false
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCategoryBudgetDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
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
        TextButton(onClick = onAction) {
            Text("See all", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun QuickAddSheet(onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
        Text("Quick Add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Amount") },
            prefix = { Text("₹") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Merchant / Category") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save Transaction")
        }
    }
}

@Composable
fun HeaderSection(userName: String) {
    val greeting = remember {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(1000)) + expandVertically()
    ) {
        Column {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FinancialSummaryCard(spent: Double, budget: Double, onClick: () -> Unit) {
    val progress = (spent / budget).coerceIn(0.0, 1.0).toFloat()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(32.dp))
            .shimmerEffect(),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Animated background pattern
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.maxDimension / 2,
                    center = Offset(size.width * 0.8f, size.height * 0.2f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = size.maxDimension / 3,
                    center = Offset(size.width * 0.1f, size.height * 0.9f)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Total Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            "₹${(budget - spent).toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Icon(
                        Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "Monthly Spend: ₹${spent.toInt()} / ₹${budget.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            "${(progress * 100).toInt()}% spent",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionChips() {
    val actions = listOf(
        "☕ Coffee" to Color(0xFF795548),
        "🚕 Auto" to Color(0xFFFBC02D),
        "🍔 Food" to Color(0xFFFF5722),
        "🛒 Grocery" to Color(0xFF4CAF50)
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(actions) { (label, color) ->
            FilterChip(
                selected = false,
                onClick = { /* TODO */ },
                label = { Text(label) },
                leadingIcon = { 
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun ProcessingStatusCard(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun BudgetAtAGlanceSection(
    budgets: List<com.cashsense.db.BudgetEntity>,
    breakdown: Map<String, Double>,
    onAddBudgetClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Budget at a Glance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddBudgetClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        if (budgets.isEmpty()) {
            Text("No budgets set. Click '+' to set one.", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(budgets) { budget ->
                    val spent = breakdown[budget.categoryId] ?: 0.0
                    BudgetCard(budget, spent)
                }
            }
        }
    }
}

@Composable
fun BudgetCard(budget: com.cashsense.db.BudgetEntity, spent: Double) {
    val progress = if (budget.amount > 0) (spent / budget.amount).coerceIn(0.0, 1.0).toFloat() else 0f
    val pct = (progress * 100).toInt()
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(60.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Refresh, contentDescription = "Toggle Chart")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (breakdown.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No data for this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Simple Donut Chart Representation
                    Box(modifier = Modifier.size(120.dp).drawBehind {
                        drawArc(
                            color = Color(0xFF2196F3),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 30f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFFE91E63),
                            startAngle = 180f,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(width = 30f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFFFFC107),
                            startAngle = 280f,
                            sweepAngle = 80f,
                            useCenter = false,
                            style = Stroke(width = 30f, cap = StrokeCap.Round)
                        )
                    }, contentAlignment = Alignment.Center) {
                        Text("Top 3", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        breakdown.entries.take(4).forEach { (cat, amount) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat, style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("₹${amount.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTransactionsCarousel(transactions: List<TransactionEntity>) {
    Column {
        Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (transactions.isEmpty()) {
            Text("No transactions found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(transactions) { tx ->
                    TransactionCarouselItem(tx)
                }
            }
        }
    }
}

@Composable
fun TransactionCarouselItem(tx: TransactionEntity) {
    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Icon(
                        imageVector = when {
                            tx.categoryId.contains("Food", true) -> Icons.Rounded.Restaurant
                            tx.categoryId.contains("Transport", true) -> Icons.Rounded.DirectionsCar
                            tx.categoryId.contains("Bills", true) -> Icons.Rounded.Receipt
                            else -> Icons.Rounded.ShoppingBag
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (tx.needsReview == 1L) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            "REVIEW",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = tx.merchant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = kotlinx.datetime.Instant.fromEpochMilliseconds(tx.date).toString().split("T").first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${if(tx.amount > 0) "+" else "-"} ₹${kotlin.math.abs(tx.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if(tx.amount > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun UpcomingBillsSection(bills: List<com.cashsense.db.RecurringPaymentEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Upcoming Bill", style = MaterialTheme.typography.labelMedium)
                Text("${bills.first().merchant}: ₹${bills.first().amount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("PAY", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(state: DashboardState, onSyncClick: () -> Unit, onNavigate: (Screen) -> Unit) {
    CenterAlignedTopAppBar(
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CashSense", fontWeight = FontWeight.Black)
            }
        },
        navigationIcon = {
            IconButton(onClick = { onNavigate(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        actions = {
            IconButton(onClick = { onNavigate(Screen.Logs) }) {
                Icon(Icons.Default.List, contentDescription = "Logs")
            }
            Box {
                IconButton(onClick = onSyncClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
                if (state.lastSyncTime == null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = 8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                }
            }
        }
    )
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition()
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500)
        )
    )

    this.onGloballyPositioned {
        size = it.size
    }.background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    )
}
