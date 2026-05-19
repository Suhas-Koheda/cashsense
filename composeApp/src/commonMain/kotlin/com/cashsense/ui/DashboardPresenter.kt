package com.cashsense.ui

import com.cashsense.db.TransactionEntity
import com.cashsense.repository.CashSenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { Dashboard, Settings, Logs }

data class DashboardState(
    val transactions: List<TransactionEntity> = emptyList(),
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
    val currentScreen: Screen = Screen.Dashboard
)

class DashboardPresenter(
    private val repository: CashSenseRepository,
    private val syncDiscovery: com.cashsense.sync.SyncDiscovery,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        observeData()
        observeScanStatus()
        observeSettings()
    }

    private fun observeSettings() {
        coroutineScope.launch {
            repository.getSetting("user_name").collect { name ->
                if (name != null) {
                    _state.value = _state.value.copy(userName = name)
                }
            }
        }
        coroutineScope.launch {
            repository.getSetting("monthly_budget").collect { budgetStr ->
                val budget = budgetStr?.toDoubleOrNull() ?: 10000.0
                _state.value = _state.value.copy(monthlyBudget = budget)
            }
        }
    }

    fun updateUserName(name: String) {
        coroutineScope.launch {
            repository.saveSetting("user_name", name)
        }
    }

    fun updateMonthlyBudget(budget: Double) {
        coroutineScope.launch {
            repository.saveSetting("monthly_budget", budget.toString())
        }
    }

    fun saveCategoryBudget(categoryId: String, amountLimit: Double) {
        coroutineScope.launch {
            val budget = com.cashsense.db.BudgetEntity(
                id = java.util.UUID.randomUUID().toString(),
                categoryId = categoryId,
                amount = amountLimit,
                month = 202605L,
                lastModified = System.currentTimeMillis(),
                isDeleted = 0L
            )
            repository.saveBudget(budget)
        }
    }

    fun navigateTo(screen: Screen) {
        _state.value = _state.value.copy(currentScreen = screen)
    }

    private fun observeData() {
        coroutineScope.launch {
            repository.getAllTransactions().collect { txList ->
                val sortedList = txList.sortedByDescending { it.date }
                val debits = sortedList.filter { 
                    it.amount < 0 || it.notes?.contains("debit", ignoreCase = true) == true
                }
                val total = debits.sumOf { it.amount }
                
                val breakdown = debits.groupBy { it.categoryId }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                    
                _state.value = _state.value.copy(
                    transactions = sortedList,
                    totalSpent = total,
                    categoryBreakdown = breakdown,
                    isLoading = false
                )
            }
        }
        coroutineScope.launch {
            repository.getAllCategories().collect { _state.value = _state.value.copy(categories = it) }
        }
        coroutineScope.launch {
            repository.getAllBudgets().collect { _state.value = _state.value.copy(budgets = it) }
        }
        coroutineScope.launch {
            repository.getAllRecurringPayments().collect { _state.value = _state.value.copy(recurringPayments = it) }
        }
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
            peers.forEach { peer ->
                syncClient.syncWithPeer(peer)
            }
            _state.value = _state.value.copy(lastSyncTime = "Just now")
        }
    }
}
