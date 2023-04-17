package com.midnightsonne.batteryinsight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, BatteryService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)

            val serviceIntent2 = Intent(context, BatteryHistoryService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent2)
        }
    }
}
