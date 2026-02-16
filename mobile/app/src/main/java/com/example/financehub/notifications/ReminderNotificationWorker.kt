package com.example.financehub.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financehub.notifications.ReminderNotificationService

class ReminderNotificationWorker(
    context: Context,
    params: WorkerParameters
): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Implementation for sending reminder notifications
        ReminderNotificationService(applicationContext).sendReminderNotification()
        return Result.success()
    }
}