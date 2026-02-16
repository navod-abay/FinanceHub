package com.example.financehub

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.example.financehub.notifications.NotificationsScheduler
import com.example.financehub.sync.SyncWorkerFactory
import com.example.financehub.notifications.ReminderNotificationService

class FinanceHubApplication: Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(this))
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d("FinanceHubApplication", "Application started, setting up notification channel and scheduling reminders")
        createNotificationChannel()
        NotificationsScheduler.scheduleDailyReminder(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            ReminderNotificationService.REMINDER_NOTIFICATION_CHANNEL_ID,
            "Reminders",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Channel for expense reminders"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        }
}