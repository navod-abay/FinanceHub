package com.example.financehub.data.backup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Base64
import android.util.Log
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.security.MessageDigest

fun uploadToNas(file: File) {
    val nasIp = "192.168.1.100"        // Your NAS IP
    val shareName = "FinanceHub_DB"          // Your NAS SMB share
    val remotePath = "financehub_backup/${file.name}"
    val username = "financeh"
    val password = "ictw@101"

    val client = SMBClient()
    Log.d("DbBackupWorker", "Connecting to NAS at $nasIp with share $shareName")
    val connection = client.connect(nasIp)
    Log.d("DbBackupWorker", "Connected to NAS")
    val authContext = AuthenticationContext(username, password.toCharArray(), "")
    Log.d("DbBackupWorker", "Authenticating with username: $username")
    val session = connection.authenticate(authContext)
    Log.d("DbBackupWorker", "Authenticated successfully")
    val share = session.connectShare(shareName) as DiskShare
    Log.d("DbBackupWorker", "Connected to share: $shareName")

    share.openFile(
        remotePath,
        setOf(AccessMask.GENERIC_WRITE),
        setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OVERWRITE_IF,
        null
    ).outputStream.use { output ->
        file.inputStream().copyTo(output)
    }

    share.close()
    session.close()
    connection.close()
    client.close()
}


fun exportDatabase(context: Context): File {
    val dbName = "expense_database"
    val dbPath = context.getDatabasePath(dbName)
    val exportDir = File(context.getExternalFilesDir(null), "db_backup")
    exportDir.mkdirs()
    if (!exportDir.exists()) {
        Log.e("DbBackupWorker", "Export directory does not exist and could not be created.")
        throw IllegalStateException("Export directory creation failed")
    }

    val exportFile = File(exportDir, dbName)
    dbPath.copyTo(exportFile, overwrite = true)
    Log.d("DbBackupWorker", "Database exported to: ${exportFile.absolutePath}")
    return exportFile
}

fun hasDbChanged(context: Context, dbFile: File): Boolean {
    val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    val lastHash = prefs.getString("last_db_hash", null)

    val digest = MessageDigest.getInstance("SHA-256")
    val newHash = Base64.encodeToString(digest.digest(dbFile.readBytes()), Base64.NO_WRAP)

    return if (newHash != lastHash) {
        prefs.edit().putString("last_db_hash", newHash).apply()
        Log.d("DbBackupWorker", "Database has changed. New hash: $newHash")
        true
    } else {
        Log.d("DbBackupWorker", "Database has not changed. Hash: $newHash")
        false
    }
}


fun isOnHomeWifi(context: Context): Boolean {
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
        Log.w("DbBackupWorker", "Location permission not granted. Cannot get SSID.")
        return false
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val network = connectivityManager.activeNetwork
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
    val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    if (!isWifi) {
        Log.d("DbBackupWorker", "Not connected to WiFi.")
        return false
    }

    val ssid = wifiManager.connectionInfo.ssid
    val cleanSsid = ssid?.replace("\"", "") ?: ""
    Log.d("DbBackupWorker", "SSID: $cleanSsid")
    // Handle <unknown ssid>
    return cleanSsid == "Abayasekera" || cleanSsid == "Abayasekera_ext2.4G"
}


class DbBackupWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext

        var onHomeWifi = isOnHomeWifi(context)
        Log.d("DbBackupWorker", "Is on home WiFi: $onHomeWifi")
        if (!onHomeWifi) return Result.retry()

        Log.d("DbBackupWorker", "Starting database export...")
        val dbFile = exportDatabase(context)
        Log.d("DbBackupWorker", "Database export completed: ${dbFile.absolutePath}")
        if (hasDbChanged(context, dbFile)) {
            Log.d("DbBackupWorker", "Database has changed, proceeding with upload...")
            try {
                uploadToNas(dbFile)
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            }
        } else Log.d("DbBackupWorker", "Database hasn't changed")


        return Result.success()
    }
}
