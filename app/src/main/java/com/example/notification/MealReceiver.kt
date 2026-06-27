package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class MealReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra("planId") ?: return
        val date = intent.getStringExtra("date") ?: ""
        val time = intent.getStringExtra("time") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val note = intent.getStringExtra("note") ?: ""
        val summary = intent.getStringExtra("summary") ?: "無指定品項"

        Log.d("MealReceiver", "Received meal alarm reminder: $title at $time")

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("targetPlanId", planId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            planId.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val descriptionText = buildString {
            append(summary)
            if (note.isNotEmpty()) {
                append(" (${note})")
            }
        }

        val notification = NotificationCompat.Builder(context, "MEAL_REMINDER_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using standard built-in android alarm icon
            .setContentTitle("用餐提醒 [$time] $title")
            .setContentText(descriptionText)
            .setSubText(date)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descriptionText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (planId.hashCode() + time.hashCode()).coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
        notificationManager.notify(notificationId, notification)
    }
}
