package com.midnightsonne.batteryinsight

import android.content.Context
import android.content.SharedPreferences

class BatteryPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("battery_history", Context.MODE_PRIVATE)

    fun saveChargeInfo(chargeInfo: ChargeInfo) {
        val editor = sharedPreferences.edit()
        val currentChargeCount = sharedPreferences.getInt("charge_count", 0)
        editor.putInt("charge_count", currentChargeCount + 1)
        editor.putString("charge_${currentChargeCount + 1}", chargeInfo.toJson())
        editor.apply()
    }

    fun getChargeInfoList(): List<ChargeInfo> {
        val chargeCount = sharedPreferences.getInt("charge_count", 0)
        val chargeInfoList = mutableListOf<ChargeInfo>()
        for (i in 1..chargeCount) {
            val json = sharedPreferences.getString("charge_$i", null)
            json?.let {
                chargeInfoList.add(ChargeInfo.fromJson(json))
            }
        }
        return chargeInfoList
    }
}
