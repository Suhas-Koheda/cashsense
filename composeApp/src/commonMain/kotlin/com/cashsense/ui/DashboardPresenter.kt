package com.cashsense.ui

import com.cashsense.db.TransactionEntity
import com.cashsense.repository.CashSenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import java.util.UUID

enum class Screen { Dashboard, Settings, Logs, Review }

data class DashboardState(
    val transactions: List<TransactionEntity> = emptyList(),
    val reviewTransactions: List<TransactionEntity> = emptyList(),
    val categories: List<com.cashsense.db.CategoryEntity> = emptyList(),
    val budgets: List<com.cashsense.db.BudgetEntity> = emptyList(),
    val recurringPayments: List<com.cashsense.db.RecurringPaymentEntity> = emptyList(),
    val totalSpent: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true,
    val processingStatus: String? = null,
    val userName: String = "User",
    val monthlyBudget: Double = 10000.0,
    val lastSyncTime: String? = null,
    val currentScreen: Screen = Screen.Dashboard,
    val currentMonth: String = "", // e.g., "05/2026"
    val monthStartEpoch: Long = 0L,
    val monthEndEpoch: Long = 0L
)

class DashboardPresenter(
    private val repository: CashSenseRepository,
    private val syncDiscovery: com.cashsense.sync.SyncDiscovery,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    
    private var dataJob: Job? = null

    init {
        // Initialize to current month
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        setMonth(now.year, now.monthNumber)
        
        observeScanStatus()
        observeSettings()
        observeReviewTransactions()
        
        coroutineScope.launch {
            repository.getAllCategories().collect { _state.value = _state.value.copy(categories = it) }
        }
        coroutineScope.launch {
            repository.getAllRecurringPayments().collect { _state.value = _state.value.copy(recurringPayments = it) }
        }
    }
    
    fun setMonth(year: Int, monthNumber: Int) {
        val startOfMonth = LocalDateTime(year, monthNumber, 1, 0, 0, 0, 0)
        val endOfMonth = if (monthNumber == 12) {
            LocalDateTime(year + 1, 1, 1, 0, 0, 0, 0)
        } else {
            LocalDateTime(year, monthNumber + 1, 1, 0, 0, 0, 0)
        }
        
        val startEpoch = startOfMonth.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val endEpoch = endOfMonth.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() - 1
        
        val monthStr = "${monthNumber.toString().padStart(2, '0')}/$year"
        
        _state.value = _state.value.copy(
            currentMonth = monthStr,
            monthStartEpoch = startEpoch,
            monthEndEpoch = endEpoch
        )
        
        restartDataObservation(startEpoch, endEpoch, year * 100L + monthNumber)
    }

    private fun restartDataObservation(startEpoch: Long, endEpoch: Long, monthValue: Long) {
        dataJob?.cancel()
        dataJob = coroutineScope.launch {
            // Wait for both to be collected? No, collect concurrently.
            launch {
                repository.getTransactionsByMonth(startEpoch, endEpoch).collect { txList ->
                    val debits = txList.filter { 
                        it.amount < 0 || it.notes?.contains("debit", ignoreCase = true) == true || it.notes?.contains("spent", ignoreCase = true) == true
                    }
                    val total = debits.sumOf { kotlin.math.abs(it.amount) }
                    
                    val breakdown = debits.groupBy { it.categoryId }
                        .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
                        
                    _state.value = _state.value.copy(
                        transactions = txList,
                        totalSpent = total,
                        categoryBreakdown = breakdown,
                        isLoading = false
                    )
                }
            }
            
            // We just observe all budgets for simplicity or filter by monthValue if supported
            launch {
                repository.getAllBudgets().collect { budgets ->
                    _state.value = _state.value.copy(budgets = budgets.filter { it.month == monthValue })
                }
            }
        }
    }
    
    private fun observeReviewTransactions() {
        coroutineScope.launch {
            repository.getTransactionsNeedingReview().collect {
                _state.value = _state.value.copy(reviewTransactions = it)
            }
        }
    }

    private fun observeSettings() {
        coroutineScope.launch {
            repository.getSetting("user_name").collect { name ->
                if (name != null) _state.value = _state.value.copy(userName = name)
            }
        }
        coroutineScope.launch {
            repository.getSetting("monthly_budget").collect { budgetStr ->
                val budget = budgetStr?.toDoubleOrNull() ?: 10000.0
                _state.value = _state.value.copy(monthlyBudget = budget)
            }
        }
    }

    fun updateUserName(name: String) = coroutineScope.launch { repository.saveSetting("user_name", name) }
    fun updateMonthlyBudget(budget: Double) = coroutineScope.launch { repository.saveSetting("monthly_budget", budget.toString()) }

    fun addTransaction(amount: Double, merchant: String, categoryId: String, isDebit: Boolean, date: Long = System.currentTimeMillis()) {
        coroutineScope.launch {
            val tx = TransactionEntity(
                id = UUID.randomUUID().toString(),
                amount = if (isDebit) -amount else amount,
                merchant = merchant,
                date = date,
                categoryId = categoryId,
                notes = if (isDebit) "Debit (Manual)" else "Credit (Manual)",
                isDeleted = 0L,
                lastModified = System.currentTimeMillis(),
                needsReview = 0L,
                originalSmsText = null
            )
            repository.addTransaction(tx)
        }
    }

    fun updateTransaction(tx: TransactionEntity) {
        coroutineScope.launch { repository.updateTransaction(tx.copy(needsReview = 0L)) }
    }

    fun deleteTransaction(id: String) {
        coroutineScope.launch { repository.deleteTransaction(id) }
    }

    fun saveCategoryBudget(categoryId: String, amountLimit: Double) {
        coroutineScope.launch {
            val parts = _state.value.currentMonth.split("/")
            if (parts.size == 2) {
                val monthValue = parts[1].toLong() * 100 + parts[0].toLong()
                val budget = com.cashsense.db.BudgetEntity(
                    id = UUID.randomUUID().toString(),
                    categoryId = categoryId,
                    amount = amountLimit,
                    month = monthValue,
                    lastModified = System.currentTimeMillis(),
                    isDeleted = 0L
                )
                repository.saveBudget(budget)
            }
        }
    }
    
    fun updateCategoryBudget(id: String, newAmount: Double) {
        coroutineScope.launch { repository.updateBudget(id, newAmount) }
    }
    
    fun deleteCategoryBudget(id: String) {
        coroutineScope.launch { repository.deleteBudget(id) }
    }

    fun navigateTo(screen: Screen) {
        _state.value = _state.value.copy(currentScreen = screen)
    }

    private fun observeScanStatus() {
        coroutineScope.launch {
            com.cashsense.sync.ScanStatus.progress.collect { status ->
                _state.value = _state.value.copy(processingStatus = status)
            }
        }
    }

    fun updateProcessingStatus(status: String?) {
        _state.value = _state.value.copy(processingStatus = status)
    }

    fun triggerSync() {
        coroutineScope.launch {
            val peers = syncDiscovery.discoverPeers()
            val syncClient = com.cashsense.sync.SyncClient(repository.database)
            peers.forEach { peer -> syncClient.syncWithPeer(peer) }
            _state.value = _state.value.copy(lastSyncTime = "Just now")
        }
    }
}
