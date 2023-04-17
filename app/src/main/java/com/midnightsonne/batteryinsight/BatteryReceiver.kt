package com.midnightsonne.batteryinsight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BatteryReceiver(private val context: Context) : BroadcastReceiver() {
    private var isCharging = false
    private var startPercentage = 0
    private var startTime = 0L
    private var currentSum: Int = 0
    private var currentCount: Int = 0
    private var minTemperature = Float.MAX_VALUE
    private var maxTemperature = Float.MIN_VALUE
    private val batteryPreferences = BatteryPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private val currentUpdateRunnable = object : Runnable {
        override fun run() {
            if (isCharging) {
                val currentNow = getCurrentBatteryCurrent(context)
                val temperature = getCurrentBatteryTemperature(context)

                currentSum += currentNow
                currentCount++

                if (temperature < minTemperature) {
                    minTemperature = temperature
                }

                if (temperature > maxTemperature) {
                    maxTemperature = temperature
                }

                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (!isCharging) {
                    isCharging = true
                    startPercentage = getCurrentBatteryPercentage(context)
                    startTime = System.currentTimeMillis()
                    currentSum = 0
                    currentCount = 0
                    handler.post(currentUpdateRunnable)
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                if (isCharging) {
                    handler.removeCallbacks(currentUpdateRunnable)

                    val sharedPreferences = context.getSharedPreferences(MainActivity.PREFERENCES_FILE, Context.MODE_PRIVATE)
                    val isEnabled2 = sharedPreferences.getBoolean(MainActivity.TEMPERATURE_UNIT, false)
                    val currentTemperatureUnit = if (isEnabled2) "°C" else "°F"
                    val endPercentage = getCurrentBatteryPercentage(context)
                    val endTime = System.currentTimeMillis()
                    val chargingTime = endTime - startTime
                    val averageCurrent = if (currentCount > 0) currentSum / currentCount else 0

                    val chargeInfo = ChargeInfo(
                        startPercentage,
                        endPercentage,
                        chargingTime,
                        averageCurrent,
                        minTemperature,
                        maxTemperature,
                        currentTemperatureUnit
                    )

                    batteryPreferences.saveChargeInfo(chargeInfo)
                    isCharging = false

                    minTemperature = Float.MAX_VALUE
                    maxTemperature = Float.MIN_VALUE

                    val localIntent = Intent("battery_history_update")
                    LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
                }
            }
        }
    }

    private fun getCurrentBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? =
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return (level.toFloat() / scale.toFloat() * 100f).toInt()
    }

    private fun getCurrentBatteryCurrent(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
    }

    private fun getCurrentBatteryTemperature(context: Context): Float {
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val sharedPreferences = context.getSharedPreferences(MainActivity.PREFERENCES_FILE, Context.MODE_PRIVATE)

        val rawTemperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        var temperature = rawTemperature.toFloat() / 10

        val isEnabled2 = sharedPreferences.getBoolean(MainActivity.TEMPERATURE_UNIT, false)
        temperature = if (isEnabled2) {
            temperature
        } else {
            temperature * 9 / 5 + 32
        }

        return temperature
    }

    companion object {
        fun createAndRegister(context: Context): BatteryReceiver {
            val batteryReceiver = BatteryReceiver(context)
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(batteryReceiver, intentFilter)
            return batteryReceiver
        }
    }
}
