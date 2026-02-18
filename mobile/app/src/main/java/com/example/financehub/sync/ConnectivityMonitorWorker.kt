package com.example.financehub.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financehub.network.ApiServiceFactory
import com.example.financehub.network.safeApiCall
import com.example.financehub.network.NetworkResult

/**
 * Background worker to monitor WiFi connectivity to home network
 * and update global connectivity state
 */
class ConnectivityMonitorWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "ConnectivityMonitorWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting connectivity monitoring")
        
        try {
            // test server reachability
            testServerReachability()
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectivity monitoring", e)
            return Result.retry()
        }
    }


    /**
     * Test if the server is reachable using the actual API
     */
    private suspend fun testServerReachability() {
        val apiService = ApiServiceFactory.apiService
        val result = safeApiCall { apiService.healthCheck() }
        val isReachable = result is NetworkResult.Success<*>
        Log.d(TAG, "Server reachability test result: $isReachable")
        ConnectivityState.updateServerReachability(isReachable)
        if (isReachable) {
            Log.d(TAG, "Server is reachable")
            // If server is reachable and we have pending syncs, trigger sync
            Log.d(TAG, "Pending sync count: ${ConnectivityState.pendingSyncCount.value}")
            if (ConnectivityState.pendingSyncCount.value > 0) {
                Log.d(TAG, "Server reachable with pending syncs - triggering sync")
                SyncTrigger.triggerSync(applicationContext)
            }
        } else if (result is NetworkResult.Error) {
            Log.d(TAG, "Server is not reachable: ${result.exception.message}")
        }
    }
}