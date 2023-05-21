package com.midnightsonne.batteryinsight

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.nfc.NfcAdapter
import android.os.*
import android.provider.Settings
import android.provider.Settings.Global
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jaredrummler.android.device.DeviceName
import com.midnightsonne.batteryinsight.databinding.ActivityMainBinding
import java.lang.Math.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREFERENCES_FILE = "my_preferences"
        const val UPDATE_INTERVAL_KEY = "update_interval"
        const val NOTIFICATION_ENABLED_KEY = "notification_enabled"
        const val TEMPERATURE_UNIT = "temperature_unit"
        const val POWER_KEY = "power"
    }

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false

    private var temperature: Double = 0.0
    private var selectedPower = 5

    private val updateBatteryInfoRunnable = Runnable {
        updateBatteryInfo()
    }

    private fun updateBatteryInfo() {
        val sharedPreferences = getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        val updateInterval = sharedPreferences.getInt(UPDATE_INTERVAL_KEY, 1000)
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        registerReceiver(null, intentFilter)?.let { batteryStatus ->
            if (batteryStatus.action == Intent.ACTION_BATTERY_CHANGED) {
                batteryInfoReceiver.onReceive(this, batteryStatus)
            }
        }

        handler.postDelayed(updateBatteryInfoRunnable, updateInterval.toLong())
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val batteryServiceIntent = Intent(this, BatteryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(batteryServiceIntent)
        } else {
            startService(batteryServiceIntent)
        }

        findViewById<ImageView>(R.id.settings).apply {
            setOnClickListener { showSettingsDialog() }
        }

        registerReceiver(batteryInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isReceiverRegistered = true
    }

    private val batteryInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            val setClrPct = ContextCompat.getColor(context, R.color.info_text)
            val setClrRed = ContextCompat.getColor(context, R.color.red)

            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct: Float = level * 100 / scale.toFloat()

            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val currentNow: Int? = getCurrentNow(context)
            val correctedCurrentNow = correctCurrentNow(currentNow, isCharging)

            val health: Int = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)

            val voltage: Int = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val deviceInfo = collectDeviceInfo(intent)
            val powerSource = getPowerSource(intent)
            val capacityInfo = collectCapacityInfo(context, batteryPct, intent)
            val fullCapacityMAh = getFullBatteryCapacity(context)

            val sharedPreferences = getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            temperature = calculateTemperature(intent, sharedPreferences)
            selectedPower = getSelectedPower(sharedPreferences)

            updateUI(deviceInfo, powerSource, capacityInfo, isCharging, correctedCurrentNow, batteryPct, setClrPct, setClrRed, temperature, voltage, fullCapacityMAh, health, sharedPreferences)

            performAutomaticTests(context)
        }
    }

    private fun getCurrentNow(context: Context): Int? {
        val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        return if (currentNow != 0) {
            val orderOfMagnitude = kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(currentNow.toDouble()))).toInt()
            if (orderOfMagnitude >= 3) {
                currentNow / 1000
            } else {
                currentNow
            }
        } else {
            null
        }
    }

    private fun correctCurrentNow(currentNow: Int?, isCharging: Boolean): Int? {
        return if (currentNow != null) {
            if (isCharging && currentNow < 0 || !isCharging && currentNow > 0) {
                -currentNow
            } else {
                currentNow
            }
        } else {
            null
        }
    }

    private fun collectDeviceInfo(intent: Intent): Map<String, String> {
        val deviceManufacturer: String = Build.MANUFACTURER
        val androidVersion = Build.VERSION.RELEASE
        val deviceModel = DeviceName.getDeviceName()
        val apiLevel = Build.VERSION.SDK_INT.toString()
        val technology: String = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

        return mapOf(
            "deviceManufacturer" to deviceManufacturer, "androidVersion" to androidVersion, "deviceModel" to deviceModel, "apiLevel" to apiLevel, "technology" to technology
        )
    }

    private fun getPowerSource(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charger"
            else -> "Battery"
        }
    }

    private fun collectCapacityInfo(context: Context, batteryPct: Float, intent: Intent): Map<String, Any> {
        val currentCapacityMAh = estimateCurrentBatteryCapacity(context, batteryPct)
        val fullCapacityMAh = getFullBatteryCapacity(context)
        val voltage: Int = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return mapOf(
            "currentCapacityMAh" to currentCapacityMAh, "fullCapacityMAh" to fullCapacityMAh, "voltage" to voltage
        )
    }

    private fun estimateCurrentBatteryCapacity(context: Context, batteryPercentage: Float): Int {
        val fullCapacityMAh = getFullBatteryCapacity(context)
        return (fullCapacityMAh * batteryPercentage / 100f).toInt()
    }

    @SuppressLint("PrivateApi")
    private fun getFullBatteryCapacity(context: Context): Int {
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        val powerProfileConstructor = Class.forName(powerProfileClass).getConstructor(Context::class.java)
        val powerProfileInstance = powerProfileConstructor.newInstance(context)

        val getBatteryCapacityMethod = Class.forName(powerProfileClass).getMethod("getBatteryCapacity")
        val batteryCapacity = getBatteryCapacityMethod.invoke(powerProfileInstance) as Double

        return batteryCapacity.toInt()
    }

    private fun calculateTemperature(intent: Intent, sharedPreferences: SharedPreferences): Double {
        temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).toDouble().div(10)

        val isEnabled2 = sharedPreferences.getBoolean(TEMPERATURE_UNIT, false)
        return if (isEnabled2) {
            temperature
        } else {
            temperature * 9 / 5 + 32.0
        }
    }

    private fun getSelectedPower(sharedPreferences: SharedPreferences): Int {
        return sharedPreferences.getString(POWER_KEY, "5V")?.replace("V", "")?.toInt() ?: 5
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(deviceInfo: Map<String, String>, powerSource: String, capacityInfo: Map<String, Any>, isCharging: Boolean, correctedCurrentNow: Int?, batteryPct: Float, setClrPct: Int, setClrRed: Int, temperature: Double, voltage: Int, fullCapacityMAh: Int, health: Int, sharedPreferences: SharedPreferences) {
        runOnUiThread {
            binding.deviceManufacturer.text = deviceInfo["deviceManufacturer"]
            binding.androidVersion.text = deviceInfo["androidVersion"]
            binding.deviceModel.text = deviceInfo["deviceModel"]
            binding.apiLevel.text = deviceInfo["apiLevel"]
            binding.batteryTechnology.text = deviceInfo["technology"]
            binding.batteryPowerSource.text = powerSource
            binding.batteryCurrent.text = correctedCurrentNow?.let { "$it mA" } ?: "Unknown"
            binding.batteryPercentage.text = "${batteryPct.toInt()}%"
            binding.batteryVoltage.text = "${capacityInfo["voltage"] as Int / 1000.0} V"
            binding.batteryCapacity.text = "~ ${capacityInfo["currentCapacityMAh"] as Int} mAh"
            binding.batteryFullCapacity.text = "${capacityInfo["fullCapacityMAh"] as Int} mAh"
            binding.batteryHealth.text = getBatteryHealthString(health)
            binding.batteryTemperature.text = if (sharedPreferences.getBoolean(TEMPERATURE_UNIT, false)) "$temperature °C" else "$temperature °F"

            val batteryCurrent = findViewById<TextView>(R.id.battery_current)
            batteryCurrent.setTypeface(null, Typeface.BOLD)
            if (isCharging) {
                batteryCurrent.setTextColor(setClrPct)
            } else {
                batteryCurrent.setTextColor(setClrRed)
            }

            val batteryPercentage = findViewById<TextView>(R.id.battery_percentage)
            batteryPercentage.setTypeface(null, Typeface.BOLD)
            if (batteryPct < 20) {
                batteryPercentage.setTextColor(setClrRed)
            } else {
                batteryPercentage.setTextColor(setClrPct)
            }

            val selectedPower = getSelectedPower(sharedPreferences)
            binding.batteryChargingWatts.text = if (isCharging) {
                val powerInMilliwatts: Double = correctedCurrentNow?.toDouble()?.times(
                    getPowerInVolts(selectedPower)
                ) ?: 0.0
                val powerInWatts: Double = powerInMilliwatts / 1000.0
                String.format("%.2f", powerInWatts) + " W"
            } else {
                val powerInMilliwatts: Double = correctedCurrentNow?.toDouble()?.times(
                    (capacityInfo["voltage"] as Int) / 1000.0
                ) ?: 0.0
                val powerInWatts: Double = powerInMilliwatts / 1000.0
                String.format("%.2f", powerInWatts) + " W"
            }

            binding.batteryTimeRemaining.text = if (isCharging) {
                calculateBatteryChargingTime(
                    batteryPct.toInt(), correctedCurrentNow?.toDouble() ?: 0.0, fullCapacityMAh
                ).let { "${it.first}h ${it.second}m" }
            } else {
                calculateBatteryRemainingTime(
                    batteryPct.toInt(), correctedCurrentNow?.toDouble() ?: 0.0, voltage / 1000.0, fullCapacityMAh
                ).let { "${it.first}h ${it.second}m" }
            }
        }
    }

    private fun getBatteryHealthString(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }

    private fun getPowerInVolts(power: Int): Double {
        return when (power) {
            5 -> 5.0
            9 -> 9.0
            10 -> 10.0
            12 -> 12.0
            15 -> 15.0
            20 -> 20.0
            else -> 5.0
        }
    }

    private fun calculateBatteryRemainingTime(
        batteryPercentage: Int, batteryCurrent: Double, batteryVoltage: Double, batteryFullCapacity: Int
    ): Pair<Int, Int> {
        val remainingCapacity = batteryPercentage / 100.0 * batteryFullCapacity
        val remainingTime = remainingCapacity / (batteryCurrent * batteryVoltage)
        val hours = remainingTime.toInt()
        val minutes = ((remainingTime - hours) * 60).toInt()
        return Pair((hours * -1), (minutes * -1))
    }

    private fun calculateBatteryChargingTime(
        batteryPercentage: Int, batteryCurrent: Double, batteryFullCapacity: Int
    ): Pair<Int, Int> {
        val remainingCapacity = (1 - batteryPercentage / 100.0) * batteryFullCapacity
        val chargingTime = remainingCapacity / batteryCurrent
        val hours = chargingTime.toInt()
        val minutes = ((chargingTime - hours) * 60).toInt()
        return Pair(hours, minutes)
    }

    @SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val updateIntervalText: TextView = dialog.findViewById(R.id.timing_text_view)
        val updateIntervalSeekBar: SeekBar = dialog.findViewById(R.id.timing_seek_bar)

        val sharedPreferences = getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        val updateInterval = sharedPreferences.getInt(UPDATE_INTERVAL_KEY, 1000)

        updateIntervalText.text = "Update Interval: ${updateInterval}ms"
        updateIntervalSeekBar.max = 19
        updateIntervalSeekBar.progress = (updateInterval - 100) / 100

        updateIntervalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val interval = (progress * 100) + 100
                updateIntervalText.text = "Update Interval: ${interval}ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val interval = (seekBar.progress * 100) + 100
                val editor = sharedPreferences.edit()
                editor.putInt(UPDATE_INTERVAL_KEY, interval)
                editor.apply()
            }
        })

        val toggleNotificationSwitch: Switch = dialog.findViewById(R.id.toggle_notification_button)
        val isEnabled = sharedPreferences.getBoolean(NOTIFICATION_ENABLED_KEY, false)
        toggleNotificationSwitch.isChecked = isEnabled

        toggleNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean(NOTIFICATION_ENABLED_KEY, isChecked)
            editor.apply()

            if (isChecked && !isNotificationPermissionGranted()) {
                openNotificationPermissionSettings()
                toggleNotificationSwitch.isChecked = false
            } else {
                startService(Intent(this, BatteryService::class.java))
            }
        }

        val toggleTemperatureSwitch: Switch = dialog.findViewById(R.id.switch_temperature)
        val isEnabled2 = sharedPreferences.getBoolean(TEMPERATURE_UNIT, false)
        toggleTemperatureSwitch.isChecked = isEnabled2

        toggleTemperatureSwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean(TEMPERATURE_UNIT, isChecked)
            editor.apply()

            val temperatureCelsius: Int = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
            temperature = if (isChecked) {
                temperatureCelsius.toDouble()
            } else {
                temperatureCelsius * 9 / 5 + 32.0
            }
        }

        val powerSeekBar: SeekBar = dialog.findViewById(R.id.voltage_seek_bar)
        val powerTextView: TextView = dialog.findViewById(R.id.voltage_text_view)

        val powerValues = listOf("5V", "9V", "10V", "12V", "15V", "20V")
        val defaultPower = "5V"

        val selectedPower = sharedPreferences.getString(POWER_KEY, defaultPower) ?: defaultPower

        powerTextView.text = "Power Adapter Output Voltage: $selectedPower"

        powerSeekBar.max = powerValues.size - 1
        powerSeekBar.progress = powerValues.indexOf(selectedPower)

        powerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                powerTextView.text = "Power Adapter Output Voltage: ${powerValues[progress]}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val editor = sharedPreferences.edit()
                editor.putString(POWER_KEY, powerValues[powerSeekBar.progress])
                editor.apply()
            }
        })

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        val versionTextView: TextView = dialog.findViewById(R.id.version_Text_View)

        versionTextView.text = "App Version $versionName Code $versionCode"

        dialog.show()
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openNotificationPermissionSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    private fun performAutomaticTests(context: Context) {
        fun checkCondition(condition: Boolean, textViewId: Int, imageViewId: Int, goodText: String, badText: String) {
            val textView: TextView = findViewById(textViewId)
            val imageView: ImageView = findViewById(imageViewId)

            textView.text = if (condition) goodText else badText
            imageView.setImageResource(if (condition) R.drawable.check_good else R.drawable.check_bad)
        }

        fun isGpsEnabled(context: Context): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }

        fun isNfcEnabled(context: Context): Boolean {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            return nfcAdapter?.isEnabled == true
        }

        fun getLastRestart(): Boolean {
            val uptimeMillis = SystemClock.elapsedRealtime()
            val uptimeHours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
            return uptimeHours < 24
        }

        fun isUsbDebuggingEnabled(context: Context): Boolean {
            return Global.getInt(context.contentResolver, Global.ADB_ENABLED, 0) != 0
        }

        fun isScreenBrightnessAutomatic(context: Context): Boolean {
            return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == 1
        }

        fun isScreenTimeoutGood(context: Context): Boolean {
            val screenTimeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            return screenTimeout <= (30 * 1000)
        }

        checkCondition(!isGpsEnabled(context), R.id.tv_gps, R.id.img_gps, "Disabled", "Enabled")
        checkCondition(!isNfcEnabled(context), R.id.tv_nfc, R.id.img_nfc, "Disabled", "Enabled")
        checkCondition(getLastRestart(), R.id.tv_last_restart, R.id.img_last_restart, "Within 24 hours", "Over 24 hours ago")
        checkCondition(!isUsbDebuggingEnabled(context), R.id.tv_usb_debugging, R.id.img_usb_debugging, "Disabled", "Enabled")
        checkCondition(isScreenBrightnessAutomatic(context), R.id.tv_screen_brightness, R.id.img_screen_brightness, "Automatic", "Not automatic")
        checkCondition(isScreenTimeoutGood(context), R.id.tv_screen_timeout, R.id.img_screen_timeout, "30 seconds or less", "30 seconds or more")
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateBatteryInfoRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateBatteryInfoRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(batteryInfoReceiver)
            isReceiverRegistered = false
        }
    }
}
