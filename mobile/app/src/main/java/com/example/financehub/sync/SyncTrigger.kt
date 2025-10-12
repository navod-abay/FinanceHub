package com.example.financehub.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Utility class for triggering and managing sync operations
 */
object SyncTrigger {
    
    private const val TAG = "SyncTrigger"
    private const val CONNECTIVITY_WORK_NAME = "connectivity_monitor"
    private const val SYNC_WORK_NAME = "data_sync"
    
    /**
     * Start periodic connectivity monitoring
     */
    fun startConnectivityMonitoring(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val periodicWorkRequest = PeriodicWorkRequestBuilder<ConnectivityMonitorWorker>(
            15, TimeUnit.MINUTES // Check every 15 minutes
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CONNECTIVITY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already running
            periodicWorkRequest
        )
        
        Log.d(TAG, "Started connectivity monitoring")
    }
    
    /**
     * Stop connectivity monitoring
     */
    fun stopConnectivityMonitoring(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CONNECTIVITY_WORK_NAME)
        Log.d(TAG, "Stopped connectivity monitoring")
    }
    
    /**
     * Trigger a one-time sync operation
     */
    fun triggerSync(context: Context, expedited: Boolean = false) {
        if (!ConnectivityState.canSync()) {
            Log.d(TAG, "Cannot sync - conditions not met")
            return
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequestBuilder = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
        
        // If expedited, try to run immediately
        if (expedited) {
            workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        
        val syncWorkRequest = workRequestBuilder.build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Replace any existing sync work
            syncWorkRequest
        )
        
        Log.d(TAG, "Triggered sync operation (expedited: $expedited)")
    }
    
    /**
     * Trigger an immediate connectivity check
     */
    fun checkConnectivityNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val immediateWorkRequest = OneTimeWorkRequestBuilder<ConnectivityMonitorWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        WorkManager.getInstance(context).enqueue(immediateWorkRequest)
        Log.d(TAG, "Triggered immediate connectivity check")
    }
    
    /**
     * Cancel all sync-related work
     */
    fun cancelAllSyncWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CONNECTIVITY_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        Log.d(TAG, "Cancelled all sync work")
    }
    
    /**
     * Get the status of sync work
     */
    fun getSyncWorkStatus(context: Context) = 
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME)
    
    /**
     * Get the status of connectivity monitoring work
     */
    fun getConnectivityWorkStatus(context: Context) = 
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(CONNECTIVITY_WORK_NAME)
}