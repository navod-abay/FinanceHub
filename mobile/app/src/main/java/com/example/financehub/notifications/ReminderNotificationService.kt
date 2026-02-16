package com.example.financehub.notifications;

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.financehub.MainActivity
import com.example.financehub.R

public class ReminderNotificationService(private val context: Context) {
    companion object {
        const val REMINDER_NOTIFICATION_CHANNEL_ID = "reminder_notification_channel"
        const val REMINDER_NOTIFICATION_ID = 2001
    }

    fun sendReminderNotification() {
        val activityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, REMINDER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_android_black_24dp)
            .setContentTitle("Daily Expense Update Reminder")
            .setContentText("Don't forget to log your expenses for today!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(REMINDER_NOTIFICATION_ID, notification.build())
    }
}
