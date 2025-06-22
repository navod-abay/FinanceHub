package com.example.financehub.data.backup

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import java.io.File
import java.security.MessageDigest

fun uploadToNas(file: File) {
    val nasIp = "192.168.1.100"        // Your NAS IP
    val shareName = "Backups"          // Your NAS SMB share
    val remotePath = "myapp_backup/${file.name}"
    val username = "nas_user"
    val password = "nas_password"

    val client = SMBClient()
    val connection = client.connect(nasIp)
    val authContext = AuthenticationContext(username, password.toCharArray(), "")
    val session = connection.authenticate(authContext)
    val share = session.connectShare(shareName) as DiskShare

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
    val dbName = "your_database_name"
    val dbPath = context.getDatabasePath(dbName)
    val exportDir = File(context.getExternalFilesDir(null), "db_backup")
    exportDir.mkdirs()

    val exportFile = File(exportDir, dbName)
    dbPath.copyTo(exportFile, overwrite = true)

    return exportFile
}

fun hasDbChanged(context: Context, dbFile: File): Boolean {
    val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    val lastHash = prefs.getString("last_db_hash", null)

    val digest = MessageDigest.getInstance("SHA-256")
    val newHash = Base64.encodeToString(digest.digest(dbFile.readBytes()), Base64.NO_WRAP)

    return if (newHash != lastHash) {
        prefs.edit().putString("last_db_hash", newHash).apply()
        true
    } else false
}


fun isOnHomeWifi(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ssid = wifiManager.connectionInfo.ssid.replace("\"", "")
    return ssid == "Your_Home_SSID"
}


class DbBackupWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext

        if (!isOnHomeWifi(context)) return Result.retry()

        val dbFile = exportDatabase(context)

        if (hasDbChanged(context, dbFile)) {
            try {
                uploadToNas(dbFile)
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            }
        }

        return Result.success()
    }
}
