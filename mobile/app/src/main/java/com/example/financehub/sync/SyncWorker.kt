package com.example.financehub.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker responsible for performing actual data synchronization with the server.
 * It uses the SyncManager to orchestrate the entire sync process.
 */

class SyncWorker  constructor(
    context: Context,
    workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync operation")

        // Check for server reachability before starting
        if (!ConnectivityState.isServerReachable.value) {
            Log.d(TAG, "Server is not reachable. Retrying sync later.")
            return Result.retry()
        }

        try {
            // Update global sync status
            ConnectivityState.updateSyncStatus(SyncStatus.SYNCING)

            // Perform the full sync using SyncManager
            val syncResult = syncManager.performFullSync()

            return when (syncResult) {
                is SyncResult.Success -> {
                    ConnectivityState.updateSyncStatus(SyncStatus.SUCCESS)
                    ConnectivityState.saveLastSyncTimestamp(
                        applicationContext,
                        System.currentTimeMillis()
                    )
                    Log.d(TAG, "Sync completed successfully")
                    Result.success()
                }
                // On success, update status and save timestamp

                is SyncResult.Failure -> {
                    // On failure, update status and log the error
                    ConnectivityState.updateSyncStatus(SyncStatus.ERROR)
                    Log.e(TAG, "Sync failed: ${syncResult.error}")
                    Result.retry() // Retry on failure
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "An unexpected error occurred during sync", e)
            ConnectivityState.updateSyncStatus(SyncStatus.ERROR)
            return Result.retry()
        }
    }
}