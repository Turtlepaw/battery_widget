package io.github.turtlepaw.batterywidget.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
import android.os.Build
import android.util.Log
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.batterywidget.services.BluetoothHeadsetBatteryManager
import io.github.turtlepaw.batterywidget.services.BluetoothLeBatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceBattery(
    val id: String,
    val name: String,
    val battery: Float,
    val isCharging: Boolean,
    val iconRes: Int,
    val paused: Boolean = false,
    val action: Intent? = null
)

class DeviceRepository(private val context: Context) {
    private val _devices = MutableStateFlow<List<DeviceBattery>>(emptyList())
    val devices: StateFlow<List<DeviceBattery>> = _devices.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updatePhoneBattery(intent)
        }
    }

    private val headsetManager = BluetoothHeadsetBatteryManager(context, this)
    private val leManager = BluetoothLeBatteryManager(context, this)

    init {
        Log.d("DeviceRepository", "Initializing DeviceRepository")
        context.registerReceiver(
            batteryReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )
        // initial fetch
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let { updatePhoneBattery(it) }
        Log.d("DeviceRepository", "DeviceRepository initialized")
    }

    private fun updatePhoneBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        Log.d(
            "DeviceRepository",
            "Phone battery updated: level=$level, scale=$scale, status=$status"
        )

        val phoneBattery = DeviceBattery(
            Build.DEVICE,
            Build.MODEL,
            level * 100f / scale,
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
            getDeviceIcon(context),
            status == BATTERY_STATUS_NOT_CHARGING,
            if (Build.MANUFACTURER == "samsung") {
                Intent().apply {
                    intent.component = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    } else {
                        ComponentName(
                            "com.samsung.android.sm",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    }
                }
            } else {
                Intent("android.intent.action.POWER_USAGE_SUMMARY")
            }
        )

        _devices.update { currentDevices ->
            listOf(phoneBattery) + currentDevices.filterNot { it.id == Build.DEVICE }
        }
    }

    fun addDevice(device: DeviceBattery) {
        Log.d("DeviceRepository", "Adding device: ${device.id}, battery=${device.battery}%")
        _devices.update { it + device }
        Log.d("DeviceRepository", "Total devices: ${_devices.value.size}")
    }

    fun updateDevice(
        predicate: (DeviceBattery) -> Boolean,
        updater: (DeviceBattery) -> DeviceBattery
    ) {
        Log.d("DeviceRepository", "Updating device with predicate")
        _devices.update { devices ->
            val updated = devices.map { if (predicate(it)) updater(it) else it }
            Log.d("DeviceRepository", "Update complete, total devices: ${updated.size}")
            updated
        }
    }

    fun cleanup() {
        context.unregisterReceiver(batteryReceiver)
        headsetManager.cleanup()
        leManager.cleanup()
    }

    fun getDeviceIcon(context: Context, codename: String = Build.DEVICE): Int {
        val isTablet = (context.resources.configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE

        return when {
            // pixel 9 series
            codename.contains("komodo") || codename.contains("caiman") -> R.drawable.ic_settings_pixel_9_pro
            codename.contains("tokay") -> R.drawable.ic_settings_pixel_9

            // pixel 8 series
            codename.contains("husky") -> R.drawable.ic_settings_pixel_8_pro
            codename.contains("shiba") -> R.drawable.ic_settings_pixel_8
            codename.contains("akita") -> R.drawable.ic_settings_pixel_8a

            // pixel 7 series
            codename.contains("cheetah") -> R.drawable.ic_settings_pixel_7_pro
            codename.contains("panther") || codename.contains("lynx") -> R.drawable.ic_settings_pixel_6a_7

            // pixel 6 series
            codename.contains("raven") -> R.drawable.ic_settings_pixel_6_pro
            codename.contains("oriole") -> R.drawable.ic_settings_pixel_6
            codename.contains("bluejay") -> R.drawable.ic_settings_pixel_6a_7

            // pixel 4/5 series
            codename.contains("redfin") || codename.contains("bramble") ||
                    codename.contains("sunfish") || codename.contains("flame") -> R.drawable.ic_settings_pixel_4_5_series

            // foldable/tablet
            codename.contains("felix") || codename.contains("comet") -> R.drawable.ic_settings_pixel_foldable
            codename.contains("tangorpro") -> R.drawable.ic_settings_pixel_tablet

            isTablet -> R.drawable.ic_generic_tablet
            else -> R.drawable.ic_generic_phone
        }
    }
}