package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.MealPlanDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object MealNotificationManager {
    private const val CHANNEL_ID = "MEAL_REMINDER_CHANNEL"
    private const val PREFS_NAME = "MealNotificationPrefs"
    private const val KEY_ENABLED_PREFIX = "reminders_enabled_"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "45天菜單用餐提醒"
            val descriptionText = "提醒您依照客製化菜單按時進食"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isReminderEnabled(context: Context, planId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED_PREFIX + planId, false)
    }

    fun setReminderEnabled(context: Context, planId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + planId, enabled).apply()
    }

    fun scheduleAlarmsForPlan(context: Context, planId: String) {
        if (!isReminderEnabled(context, planId)) {
            cancelAlarmsForPlan(context, planId)
            return
        }

        val database = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val days = database.mealPlanDayDao().getDaysForPlanDirect(planId)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch

                // Clear previous first
                cancelAlarmsForPlan(context, planId)

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

                for (day in days) {
                    // Only schedule for today and future days
                    if (day.date + (24 * 60 * 60 * 1000) < now) continue

                    for (meal in day.meals) {
                        val mealTimeMs = parseMealTimeToMs(day.date, meal.time)
                        if (mealTimeMs < now) continue

                        val intent = Intent(context, MealReceiver::class.java).apply {
                            putExtra("planId", planId)
                            putExtra("date", dateFormat.format(day.date))
                            putExtra("time", meal.time)
                            putExtra("title", meal.title)
                            putExtra("note", meal.note)
                            val summary = meal.items.joinToString(", ") { "${it.menuItemName} x${it.quantity}${it.unit}" }
                            putExtra("summary", summary)
                        }

                        // Generate a unique requestCode per meal alarm
                        val requestCode = (planId.hashCode() + day.dayIndex * 100 + meal.time.hashCode()).coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
                        
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                mealTimeMs,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                mealTimeMs,
                                pendingIntent
                            )
                        }
                    }
                }
                Log.d("MealNotification", "Scheduled alarms successfully for plan: $planId")
            } catch (e: Exception) {
                Log.e("MealNotification", "Error scheduling alarms", e)
            }
        }
    }

    fun cancelAlarmsForPlan(context: Context, planId: String) {
        val database = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val days = database.mealPlanDayDao().getDaysForPlanDirect(planId)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch

                for (day in days) {
                    for (meal in day.meals) {
                        val intent = Intent(context, MealReceiver::class.java)
                        val requestCode = (planId.hashCode() + day.dayIndex * 100 + meal.time.hashCode()).coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                        )
                        if (pendingIntent != null) {
                            alarmManager.cancel(pendingIntent)
                            pendingIntent.cancel()
                        }
                    }
                }
                Log.d("MealNotification", "Cancelled alarms successfully for plan: $planId")
            } catch (e: Exception) {
                Log.e("MealNotification", "Error cancelling alarms", e)
            }
        }
    }

    private fun parseMealTimeToMs(dayDate: Long, timeStr: String): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayDate

        // Default "睡前" (bedtime) to 22:00 as specified in requirement 4
        val formattedTime = if (timeStr == "睡前") "22:00" else timeStr

        val parts = formattedTime.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: 12
            val minute = parts[1].toIntOrNull() ?: 0
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
