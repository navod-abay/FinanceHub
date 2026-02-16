package com.example.financehub.notifications

import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit
import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar

object NotificationsScheduler {
    private const val DAILY_REMINDER_WORK_NAME = "DailyReminderWorker"
    private const val TAG = "NotificationScheduler"
    fun scheduleDailyReminder(context: Context){
        Log.d(TAG, "Scheduling daily reminder notifications")
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        val timeDifference = TimeUnit.MILLISECONDS.toMinutes(dueDate.timeInMillis - currentDate.timeInMillis)
        val dailyReminderRequest = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
                ).setInitialDelay(timeDifference, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                DAILY_REMINDER_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                dailyReminderRequest
            )
    }

    fun cancelDailyReminder(context: Context){
        WorkManager.getInstance(context)
            .cancelUniqueWork(DAILY_REMINDER_WORK_NAME)
    }
}