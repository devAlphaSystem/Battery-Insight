package com.midnightsonne.batteryinsight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File

class BatteryService : Service() {
    companion object {
        private const val CHANNEL_ID = "battery_info_channel"
        private const val NOTIFICATION_ID = 1
        private const val BATTERY_HISTORY_FILENAME = "battery_history.txt"
    }

    private val sharedPreferences by lazy {
        applicationContext.getSharedPreferences(MainActivity.PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    private val updateInterval: Int
        get() = sharedPreferences.getInt(
            MainActivity.UPDATE_INTERVAL_KEY,
            1000
        )

    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            val currentNow = getCurrentNow(applicationContext) ?: 0

            updateNotification(currentNow)
            saveBatteryInfo(currentNow)

            handler.postDelayed(this, updateInterval.toLong())
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status: Int = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val isCharging: Boolean =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val currentNow: Int? = context?.let { getCurrentNow(it) }
            val correctedCurrentNow = if (currentNow != null) {
                if (isCharging && currentNow < 0 || !isCharging && currentNow > 0) {
                    -currentNow
                } else {
                    currentNow
                }
            } else {
                null
            }

            if (correctedCurrentNow != null) {
                updateNotification(correctedCurrentNow)
                saveBatteryInfo(correctedCurrentNow)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun getCurrentNow(context: Context): Int? {
        val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        return if (currentNow != 0) {
            val orderOfMagnitude =
                kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(currentNow.toDouble()))).toInt()
            if (orderOfMagnitude >= 3) {
                currentNow / 1000
            } else {
                currentNow
            }
        } else {
            val file = File("/sys/class/power_supply/battery/current_now")
            val currentFromFile = file.readText().trim().toIntOrNull()
            currentFromFile?.div(1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        val initialCurrent = getCurrentNow(applicationContext) ?: 0
        startForeground(NOTIFICATION_ID, createNotification(initialCurrent))

        handler.post(updateRunnable)

        return START_STICKY
    }

    private fun createNotification(correctedCurrentNow: Int): Notification {
        val channel =
            NotificationChannel(CHANNEL_ID, "Battery Info", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Info")
            .setContentText("Current: $correctedCurrentNow mA")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        return builder.build()
    }

    private fun isNotificationPermissionGranted(): Boolean {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.areNotificationsEnabled()
    }

    private fun updateNotification(correctedCurrentNow: Int) {
        val isNotificationEnabled =
            sharedPreferences.getBoolean(MainActivity.NOTIFICATION_ENABLED_KEY, true)
        val hasNotificationPermission = isNotificationPermissionGranted()

        if (isNotificationEnabled && hasNotificationPermission) {
            val notification = createNotification(correctedCurrentNow)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun saveBatteryInfo(currentNow: Int) {
        val batteryHistoryFile = File(filesDir, BATTERY_HISTORY_FILENAME)

        val historyString = if (batteryHistoryFile.exists()) {
            batteryHistoryFile.readText()
        } else {
            ""
        }

        val currentTime = System.currentTimeMillis()
        val newEntry = "$currentTime,$currentNow\n"

        val newHistoryString = historyString + newEntry

        batteryHistoryFile.writeText(newHistoryString)
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        handler.removeCallbacks(updateRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}