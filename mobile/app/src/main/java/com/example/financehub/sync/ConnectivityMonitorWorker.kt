package com.example.financehub.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
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
        const val HOME_WIFI_SSID_1 = "Abayasekera"
        const val HOME_WIFI_SSID_2 = "Abayasekera_ext2.4G"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting connectivity monitoring")
        
        try {
            // Initial check
            checkAndUpdateConnectivity()
            
            // If we're connected to home WiFi, test server reachability
            if (ConnectivityState.isConnectedToHomeWifi.value) {
                testServerReachability()
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectivity monitoring", e)
            return Result.retry()
        }
    }

    /**
     * Check WiFi connectivity and update global state
     */
    private fun checkAndUpdateConnectivity() {
        // Use the helper which validates permissions and network transport
        val isConnected = isOnHomeWifi(applicationContext)
        val wasConnected = ConnectivityState.isConnectedToHomeWifi.value

        ConnectivityState.updateWifiState(isConnected)

        Log.d(TAG, "WiFi state: connected=$isConnected, wasConnected=$wasConnected")

        // If we just connected to home WiFi, trigger sync check
        if (isConnected && !wasConnected) {
            Log.d(TAG, "Connected to home WiFi - will test server reachability")
        } else if (!isConnected && wasConnected) {
            Log.d(TAG, "Disconnected from home WiFi")
            ConnectivityState.updateServerReachability(false)
        }
    }

    /**
     * Test if the server is reachable using the actual API
     */
    private suspend fun testServerReachability() {
        val apiService = ApiServiceFactory.apiService
        val result = safeApiCall { apiService.healthCheck() }
        val isReachable = result is NetworkResult.Success<*>
        ConnectivityState.updateServerReachability(isReachable)
        if (isReachable) {
            Log.d(TAG, "Server is reachable")
            // If server is reachable and we have pending syncs, trigger sync
            if (ConnectivityState.pendingSyncCount.value > 0) {
                Log.d(TAG, "Server reachable with pending syncs - triggering sync")
                SyncTrigger.triggerSync(applicationContext)
            }
        } else if (result is NetworkResult.Error) {
            Log.d(TAG, "Server is not reachable: ${result.exception.message}")
        }
    }

    /**
     * Check if device is connected to home WiFi
     * Reuses existing logic from DbBackupWorker
     */
    private fun isOnHomeWifi(context: Context): Boolean {
        // Check for location permission (required for SSID access on Android 8+)
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            Log.w(TAG, "Location permission not granted. Cannot get SSID.")
            return false
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifi) {
            Log.d(TAG, "Not connected to WiFi.")
            return false
        }

        val ssid = wifiManager.connectionInfo?.ssid
        val cleanSsid = ssid?.replace("\"", "")?.trim() ?: ""
        Log.d(TAG, "Current SSID: $cleanSsid")

        // Check against both home network SSIDs
        return cleanSsid == HOME_WIFI_SSID_1 || cleanSsid == HOME_WIFI_SSID_2
    }
}