package com.midnightsonne.batteryinsight

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File

class BatteryHistoryService : Service() {
    private val BATTERY_HISTORY_FILENAME = "battery_history.txt"
    private val updateInterval: Long = 1000 * 60 * 60 * 24 // 24 hours

    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime: Long = 0

    private val updateRunnable = object : Runnable {
        override fun run() {
            saveBatteryHistory()
            lastUpdateTime = SystemClock.elapsedRealtime()
            handler.postDelayed(
                this,
                lastUpdateTime + updateInterval - SystemClock.elapsedRealtime()
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        lastUpdateTime = SystemClock.elapsedRealtime()
        handler.post(updateRunnable)
    }

    private fun saveBatteryHistory() {
        val batteryHistoryFile = File(filesDir, BATTERY_HISTORY_FILENAME)

        val historyString = if (batteryHistoryFile.exists()) {
            batteryHistoryFile.readText()
        } else {
            ""
        }

        val currentTime = System.currentTimeMillis()
        val currentNow = getCurrentNow(applicationContext) ?: 0
        val newEntry = "$currentTime,$currentNow\n"

        val newHistoryString = historyString + newEntry

        batteryHistoryFile.writeText(newHistoryString)

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("battery_history_update"))
    }

    private fun getCurrentNow(context: Context): Int? {
        val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager?
        val currentNow: Int? = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: NoSuchMethodError) {
            null
        }

        return if (currentNow != null && currentNow != 0) {
            val orderOfMagnitude =
                kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(currentNow.toDouble()))).toInt()
            if (orderOfMagnitude >= 3) {
                currentNow / 1000
            } else {
                currentNow
            }
        } else {
            try {
                val file = File("/sys/class/power_supply/battery/current_now")
                val currentFromFile = file.readText().trim().toIntOrNull()
                currentFromFile?.div(1000)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}
