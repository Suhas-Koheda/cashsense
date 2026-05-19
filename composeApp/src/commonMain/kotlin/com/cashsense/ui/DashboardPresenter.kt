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
    val currentMonth: String = "", 
    val monthStartEpoch: Long = 0L,
    val monthEndEpoch: Long = 0L,
    val isAllTime: Boolean = false
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
        
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthStr = "${monthNames[monthNumber - 1]} $year"
        
        _state.value = _state.value.copy(
            currentMonth = monthStr,
            monthStartEpoch = startEpoch,
            monthEndEpoch = endEpoch,
            isAllTime = false
        )
        
        restartDataObservation(startEpoch, endEpoch, year * 100L + monthNumber)
    }

    fun setAllTime() {
        _state.value = _state.value.copy(
            currentMonth = "All Time",
            monthStartEpoch = 0L,
            monthEndEpoch = Long.MAX_VALUE,
            isAllTime = true
        )
        restartDataObservation(0L, Long.MAX_VALUE, -1L)
    }

    private fun restartDataObservation(startEpoch: Long, endEpoch: Long, monthValue: Long) {
        dataJob?.cancel()
        dataJob = coroutineScope.launch {
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
            
            launch {
                repository.getAllBudgets().collect { budgets ->
                    _state.value = _state.value.copy(budgets = if (monthValue == -1L) budgets else budgets.filter { it.month == monthValue })
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
        coroutineScope.launch { repository.getSetting("user_name").collect { name -> if (name != null) _state.value = _state.value.copy(userName = name) } }
        coroutineScope.launch { repository.getSetting("monthly_budget").collect { budgetStr -> _state.value = _state.value.copy(monthlyBudget = budgetStr?.toDoubleOrNull() ?: 10000.0) } }
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
            if (_state.value.isAllTime) return@launch // Don't save budget for all time
            val parts = _state.value.currentMonth.split(" ")
            if (parts.size == 2) {
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val monthIdx = monthNames.indexOf(parts[0]) + 1
                val monthValue = parts[1].toLong() * 100 + monthIdx.toLong()
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

    fun navigateTo(screen: Screen) { _state.value = _state.value.copy(currentScreen = screen) }

    private fun observeScanStatus() {
        coroutineScope.launch {
            com.cashsense.sync.ScanStatus.progress.collect { status -> _state.value = _state.value.copy(processingStatus = status) }
        }
    }

    fun updateProcessingStatus(status: String?) { _state.value = _state.value.copy(processingStatus = status) }

    fun triggerSync() {
        coroutineScope.launch {
            val peers = syncDiscovery.discoverPeers()
            val syncClient = com.cashsense.sync.SyncClient(repository.database)
            peers.forEach { peer -> syncClient.syncWithPeer(peer) }
            _state.value = _state.value.copy(lastSyncTime = "Just now")
        }
    }
    
    // Quick Action Chips Settings
    suspend fun getChipSetting(key: String, defaultValue: String): String {
        var value = defaultValue
        repository.getSetting(key).collect { v -> if (v != null) value = v }
        return value
    }
    fun saveChipSetting(key: String, value: String) = coroutineScope.launch { repository.saveSetting(key, value) }
}
