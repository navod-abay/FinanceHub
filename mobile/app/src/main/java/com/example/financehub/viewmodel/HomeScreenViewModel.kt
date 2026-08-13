package com.example.financehub.viewmodel


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.dao.TargetRef
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.database.Target
import com.example.financehub.data.datastore.AnalyticsDatastore
import com.example.financehub.data.datastore.PersistentAnalyticData
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.repository.HomeScreenAnalytics
import com.example.financehub.data.repository.SyncRepositoryResult
import com.example.financehub.data.repository.SyncRepositoryState
import com.example.financehub.data.repository.WishlistRepository
import com.example.financehub.sync.SyncProgress
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import java.util.Calendar


class HomeScreenViewModel(
    private val repository: ExpenseRepository,
    private val wishlistRepository: WishlistRepository,
) : ViewModel() {

    private  val _highestTag = MutableStateFlow<TagWithAmount>(TagWithAmount("Loading...", 0))
    val highestTag: StateFlow<TagWithAmount> = _highestTag


    private val _wishlistValue = MutableStateFlow<Pair<Int,Int>>(Pair<Int, Int>(0,0))

    val wishlistValue: StateFlow<Pair<Int, Int>> = _wishlistValue
    // LiveData for monthly total
    private val _monthlyTotal = MutableStateFlow<Int>(0)
    val monthlyTotal: StateFlow<Int> = _monthlyTotal

    private val _missedTargets = MutableStateFlow(0)
    val missedTargets: StateFlow<Int> = _missedTargets
    private val _totalTargets = MutableStateFlow(0)
    val totalTargets: StateFlow<Int> = _totalTargets


    private val _operationalExpenses = MutableStateFlow<Int>(0)
    val operationalExpenses: StateFlow<Int> = _operationalExpenses

    private val _operationalExpensesAvg = MutableStateFlow<Int>(0)
    val operationalExpensesAvgFTM: StateFlow<Int> = _operationalExpensesAvg

    private val _operationalExpensesAvgIncrease = MutableStateFlow<Int>(0)
    val operationalExpensesAvgIncFTM: StateFlow<Int> = _operationalExpensesAvgIncrease


    // Sync-related state
    val syncState: StateFlow<SyncRepositoryState> = repository.getSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncRepositoryState.IDLE)
    
    val syncProgress: StateFlow<SyncProgress> = repository.getSyncProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProgress())


    val targetProgress: StateFlow<List<TargetRef>> = repository.getMonthlyTargetProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _syncResult = MutableStateFlow<SyncRepositoryResult?>(null)

    private val _hasPendingSync = MutableStateFlow(false)
    val hasPendingSync: StateFlow<Boolean> = _hasPendingSync
    val calendar = Calendar.getInstance()

    val currentDay: Int = calendar.get(Calendar.DAY_OF_MONTH)
    val runningAverage  : StateFlow<HomeScreenAnalytics> = repository.persistentAnalyticFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeScreenAnalytics(0,0.0f,0.0f,0.0f,0,0.0f,0.0f,0.0f)
    )

    init {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        viewModelScope.launch {
            combine(
                repository.getCurrentMonthTotal(currentMonth + 1, currentYear),
                repository.getTopTagForMonth(currentMonth + 1, currentYear),
            ) { total, topTag ->
                Pair(total, topTag)
            }.map { (total, topTag) ->
                _monthlyTotal.value = total
                _highestTag.value = TagWithAmount(topTag.tag, 0)
                Log.d("HomeScreenViewModel", "Total: $total, Top Tag: ${topTag.tag}")
            }.collect {}
        }
        viewModelScope.launch {
            repository.getMonthlyTargetsStats().collect { (missed, total) ->
                _missedTargets.value = missed
                _totalTargets.value = total
            }
        }
        viewModelScope.launch {
            wishlistRepository.getWishlistValues().collect { values ->
                _wishlistValue.value = values
            }
        }

        viewModelScope.launch {
            repository.getOperationalExpensesForTheMonth(currentMonth +1, currentYear).collect{ value ->
                _operationalExpenses.value = value
            }
        }
        
        // Monitor pending sync status
        viewModelScope.launch {
            while (true) {
                _hasPendingSync.value = repository.hasPendingSync()
                kotlinx.coroutines.delay(5000) // Check every 5 seconds
            }
        }
    }

    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        viewModelScope.launch {
            _syncResult.value = null
            val result = repository.triggerSync()
            _syncResult.value = result
        }
    }

    /**
     * Clear sync result message
     */
    fun clearSyncResult() {
        _syncResult.value = null
    }

    /**
     * Check if sync is currently in progress
     */
    fun isSyncing(): Boolean = syncState.value == SyncRepositoryState.SYNCING

    /**
     * Get sync status message for UI
     */
    fun getSyncStatusMessage(): String {
        return when (syncState.value) {
            SyncRepositoryState.IDLE -> {
                if (hasPendingSync.value) "Pending sync" else "Up to date"
            }
            SyncRepositoryState.SYNCING -> {
                val progress = syncProgress.value
                if (progress.stage.isNotEmpty()) progress.stage else "Syncing..."
            }
            SyncRepositoryState.ERROR -> "Sync error"
        }
    }
}
