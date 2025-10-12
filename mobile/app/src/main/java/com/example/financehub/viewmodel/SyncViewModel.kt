package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.repository.SyncRepositoryResult
import com.example.financehub.data.repository.SyncRepositoryState
import com.example.financehub.sync.ConnectivityState
import com.example.financehub.sync.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel for managing sync operations across the app
 * This provides a centralized way to handle sync state and operations
 */
class SyncViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    // Sync state from repository
    val syncState: StateFlow<SyncRepositoryState> = repository.getSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncRepositoryState.IDLE)

    // Sync progress from repository
    val syncProgress: StateFlow<SyncProgress> = repository.getSyncProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProgress())

    // Last sync result
    private val _lastSyncResult = MutableStateFlow<SyncRepositoryResult?>(null)
    val lastSyncResult: StateFlow<SyncRepositoryResult?> = _lastSyncResult

    // Combined sync status for UI
    val syncStatus: StateFlow<SyncStatus> = combine(
        syncState,
        ConnectivityState.isConnectedToHomeWifi,
        ConnectivityState.isServerReachable,
        repository.hasPendingSync,
        syncProgress
    ) { state, isConnectedToHomeWifi, isServerReachable, pending, progress ->
        when {
            state == SyncRepositoryState.SYNCING -> SyncStatus.Syncing(progress.stage)
            state == SyncRepositoryState.ERROR -> SyncStatus.Error("Sync failed")
            !isConnectedToHomeWifi || !isServerReachable -> SyncStatus.Offline
            pending -> SyncStatus.PendingSync
            else -> SyncStatus.UpToDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.UpToDate)

    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        if (syncState.value == SyncRepositoryState.SYNCING) {
            return // Already syncing
        }

        viewModelScope.launch {
            _lastSyncResult.value = null
            val result = repository.triggerSync()
            _lastSyncResult.value = result
        }
    }

    /**
     * Clear last sync result
     */
    fun clearSyncResult() {
        _lastSyncResult.value = null
    }

    /**
     * Get user-friendly sync status message
     */
    fun getSyncStatusMessage(): String {
        return when (val status = syncStatus.value) {
            is SyncStatus.Syncing -> status.stage.takeIf { it.isNotEmpty() } ?: "Syncing..."
            is SyncStatus.Error -> status.message
            SyncStatus.Offline -> "Offline"
            SyncStatus.PendingSync -> "Pending sync"
            SyncStatus.UpToDate -> "Up to date"
        }
    }

    /**
     * Check if sync can be triggered
     */
    fun canTriggerSync(): Boolean {
        return ConnectivityState.canSync()
    }

    /**
     * Get sync button text based on current state
     */
    fun getSyncButtonText(): String {
        return when (syncState.value) {
            SyncRepositoryState.SYNCING -> "Syncing..."
            SyncRepositoryState.ERROR -> "Retry Sync"
            SyncRepositoryState.IDLE -> {
                if (repository.hasPendingSync.value) "Sync Now" else "Sync"
            }
        }
    }

    /**
     * Check if we should show sync indicator
     */
    fun shouldShowSyncIndicator(): Boolean {
        return repository.hasPendingSync.value ||
               syncState.value == SyncRepositoryState.SYNCING ||
               syncState.value == SyncRepositoryState.ERROR
    }
}

/**
 * Sealed class representing different sync states for UI
 */
sealed class SyncStatus {
    object UpToDate : SyncStatus()
    object Offline : SyncStatus()
    object PendingSync : SyncStatus()
    data class Syncing(val stage: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}