package com.example.financehub.data.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WifiConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (isOnHomeWifi(context)) {
            val oneTimeWork = OneTimeWorkRequestBuilder<DbBackupWorker>().build()
            WorkManager.getInstance(context).enqueue(oneTimeWork)
        }
    }
}