package com.midnightsonne.batteryinsight

import com.google.gson.Gson

data class ChargeInfo(
    val startPercentage: Int,
    val endPercentage: Int,
    val chargingTimeMillis: Long,
    val averageCurrent: Int,
    val minTemperature: Float,
    val maxTemperature: Float,
    val temperatureUnit: String
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): ChargeInfo {
            return Gson().fromJson(json, ChargeInfo::class.java)
        }
    }
}
