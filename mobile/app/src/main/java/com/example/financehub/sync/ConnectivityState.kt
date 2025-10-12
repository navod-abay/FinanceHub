package com.example.financehub.sync

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global state manager for network connectivity and sync status
 */
object ConnectivityState {
    
    // WiFi connection state
    private val _isConnectedToHomeWifi = MutableStateFlow(false)
    val isConnectedToHomeWifi: StateFlow<Boolean> = _isConnectedToHomeWifi.asStateFlow()
    
    // Server connectivity state
    private val _isServerReachable = MutableStateFlow(false)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()
    
    // Sync status
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // Last sync timestamp
    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()
    
    // Pending sync count
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()
    
    /**
     * Update WiFi connection state
     */
    fun updateWifiState(isConnected: Boolean) {
        _isConnectedToHomeWifi.value = isConnected
        if (!isConnected) {
            // If WiFi is disconnected, server is also unreachable
            _isServerReachable.value = false
        }
    }
    
    /**
     * Update server reachability state
     */
    fun updateServerReachability(isReachable: Boolean) {
        _isServerReachable.value = isReachable
    }
    
    /**
     * Update sync status
     */
    fun updateSyncStatus(status: SyncStatus) {
        _syncStatus.value = status
    }
    
    /**
     * Update last sync timestamp
     */
    fun updateLastSyncTimestamp(timestamp: Long) {
        _lastSyncTimestamp.value = timestamp
    }
    
    /**
     * Update pending sync count
     */
    fun updatePendingSyncCount(count: Int) {
        _pendingSyncCount.value = count
    }
    
    /**
     * Check if sync is possible (both WiFi and server are available)
     */
    fun canSync(): Boolean {
        return isConnectedToHomeWifi.value && isServerReachable.value && syncStatus.value != SyncStatus.SYNCING
    }
    
    /**
     * Get last sync timestamp from SharedPreferences
     */
    fun loadLastSyncTimestamp(context: Context) {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_sync_timestamp", 0L)
        if (timestamp > 0) {
            _lastSyncTimestamp.value = timestamp
        }
    }
    
    /**
     * Save last sync timestamp to SharedPreferences
     */
    fun saveLastSyncTimestamp(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
        _lastSyncTimestamp.value = timestamp
    }
}

/**
 * Enum representing different sync states
 */
enum class SyncStatus {
    IDLE,           // Not syncing
    SYNCING,        // Currently syncing
    SUCCESS,        // Last sync was successful
    ERROR,          // Last sync failed
    PENDING         // Sync is queued/waiting
}