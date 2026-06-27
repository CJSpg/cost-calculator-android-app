package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot detected! Re-scheduling all meal reminder alarms...")
            
            val database = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val plans = database.mealPlanDao().getAllMealPlans().first()
                    for (plan in plans) {
                        if (MealNotificationManager.isReminderEnabled(context, plan.id)) {
                            Log.d("BootReceiver", "Re-scheduling alarm for meal plan: ${plan.id}")
                            MealNotificationManager.scheduleAlarmsForPlan(context, plan.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule reminders after reboot", e)
                }
            }
        }
    }
}
