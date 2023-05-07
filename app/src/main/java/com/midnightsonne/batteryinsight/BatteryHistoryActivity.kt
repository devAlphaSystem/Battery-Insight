package com.midnightsonne.batteryinsight

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BatteryHistoryActivity : AppCompatActivity() {
    private lateinit var batteryReceiver: BroadcastReceiver
    private lateinit var batteryHistoryContainer: LinearLayout
    private lateinit var emptyHistoryMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        batteryHistoryContainer = findViewById(R.id.battery_history_container)
        emptyHistoryMessage = findViewById(R.id.empty_history_message)

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "battery_history_update") {
                    updateBatteryHistoryUI()
                }
            }
        }

        updateBatteryHistoryUI()
    }

    private fun updateBatteryHistoryUI() {
        val batteryPreferences = BatteryPreferences(this)
        val chargeInfoList = batteryPreferences.getChargeInfoList()
        batteryHistoryContainer.removeAllViews()
        if (chargeInfoList.isNotEmpty()) {
            emptyHistoryMessage.visibility = View.GONE
        } else {
            emptyHistoryMessage.visibility = View.VISIBLE
        }
        chargeInfoList.reversed().forEach { chargeInfo ->
            addChargeInfoView(chargeInfo)
        }
    }

    private fun formatChargingTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @SuppressLint("StringFormatMatches", "SetTextI18n")
    private fun addChargeInfoView(chargeInfo: ChargeInfo) {
        val inflater = LayoutInflater.from(this)
        val chargeInfoView =
            inflater.inflate(R.layout.item_charge_info, batteryHistoryContainer, false)

        val startPercentageView = chargeInfoView.findViewById<TextView>(R.id.start_percentage)
        val endPercentageView = chargeInfoView.findViewById<TextView>(R.id.end_percentage)
        val chargingTimeView = chargeInfoView.findViewById<TextView>(R.id.charging_time)
        val averageCurrentView = chargeInfoView.findViewById<TextView>(R.id.average_current)
        val minTemperatureView = chargeInfoView.findViewById<TextView>(R.id.min_temperature)
        val maxTemperatureView = chargeInfoView.findViewById<TextView>(R.id.max_temperature)

        startPercentageView.text = getString(R.string.start_percentage, chargeInfo.startPercentage)
        endPercentageView.text = getString(R.string.end_percentage, chargeInfo.endPercentage)
        chargingTimeView.text =
            getString(R.string.charging_time) + " " + formatChargingTime(chargeInfo.chargingTimeMillis)
        averageCurrentView.text = getString(R.string.average_current, chargeInfo.averageCurrent)
        minTemperatureView.text = getString(
            R.string.min_temperature,
            chargeInfo.minTemperature
        ) + chargeInfo.temperatureUnit
        maxTemperatureView.text = getString(
            R.string.max_temperature,
            chargeInfo.maxTemperature
        ) + chargeInfo.temperatureUnit

        batteryHistoryContainer.addView(chargeInfoView)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            batteryReceiver, IntentFilter("battery_history_update")
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(batteryReceiver)
    }
}
