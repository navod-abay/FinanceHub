package com.example.financehub

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.navigation.compose.rememberNavController
import com.example.financehub.navigation.NavGraph
import com.example.financehub.sync.ConnectivityState
import com.example.financehub.sync.SyncTrigger

class MainActivity : ComponentActivity() {
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")
        
        // Register location permission launcher
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Location permission granted")
            } else {
                Log.w("MainActivity", "Location permission denied. SSID may be unavailable.")
            }
            // Start the sync system after permission decision (granted or denied)
            initializeSyncSystem()
        }

        // Initialize sync system (request permission if needed)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeSyncSystem()
        } else {
            // Ask for location permission; initialization continues in callback
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        



        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavGraph(navController = navController)
        }

    }
    
    /**
     * Initialize the sync system
     */
    private fun initializeSyncSystem() {
        Log.d("MainActivity", "Initializing sync system")
        
        // Load last sync timestamp from preferences
        ConnectivityState.loadLastSyncTimestamp(this)
        
        // Start connectivity monitoring
        SyncTrigger.startConnectivityMonitoring(this)
        
        // Trigger an immediate connectivity check
        SyncTrigger.checkConnectivityNow(this)
        
        Log.d("MainActivity", "Sync system initialized")
    }
}
