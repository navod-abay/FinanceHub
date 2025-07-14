package com.example.financehub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.financehub.data.backup.DbBackupWorker
import com.example.financehub.navigation.NavGraph
import java.util.concurrent.TimeUnit

fun scheduleBackupWorker(context: Context) {
    Log.d("MainActivity", "Scheduling backup worker")
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED) // Only Wi-Fi
        .build()

    val workRequest = PeriodicWorkRequestBuilder<DbBackupWorker>(
        1, TimeUnit.DAYS
    ).setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "RoomBackupWork",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}


class MainActivity : ComponentActivity() {
    private val REQUEST_CODE_RESTORE_PERMISSION = 1001
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")
        scheduleBackupWorker(this)
/*
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, now launch SAF picker
                openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/db", "application/vnd.sqlite3", "/*/*"))
            } else {
                Log.e("MainActivity", "Permission denied. Cannot restore backup.")
            }
        }

        // SAF launcher for picking a backup file
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val prefs = getSharedPreferences("restore_prefs", Context.MODE_PRIVATE)
                com.example.financehub.data.RestoreHelper.restoreFromBackup(this, uri)
                prefs.edit().putBoolean("restored", true).apply()
            } else {
                Log.e("MainActivity", "No file selected for restore.")
            }
        }

        val prefs = getSharedPreferences("restore_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("restored").apply()
        Log.d("MainActivity", "Restore preferences cleared")
        val restored = prefs.getBoolean("restored", false)
        if (!restored) {
            // If permission is needed, request it, otherwise launch SAF picker directly
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/db", "application/vnd.sqlite3", "*/*"))
            }
        }
        */
 */

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavGraph(navController = navController)
        }

    }
}
