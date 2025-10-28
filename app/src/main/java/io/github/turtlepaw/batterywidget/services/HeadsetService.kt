package io.github.turtlepaw.batterywidget.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.batterywidget.data.DeviceBattery
import io.github.turtlepaw.batterywidget.data.DeviceRepository

class BluetoothHeadsetBatteryManager(
    private val context: Context,
    private val repository: DeviceRepository
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var headsetProxy: BluetoothHeadset? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> handleConnectionChange(intent)
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> handleBatteryChange(
                    intent
                )

                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> handleBatteryChange(intent)
            }
        }
    }

    init {
        Log.d("HeadsetBattery", "Initializing BluetoothHeadsetBatteryManager")
        Log.d("HeadsetBattery", "Bluetooth adapter available: ${bluetoothAdapter != null}")
        Log.d("HeadsetBattery", "Has permission: ${hasBluetoothPermission()}")

        if (hasBluetoothPermission()) {
            registerReceivers()
            connectHeadsetProfile()
        } else {
            Log.w(
                "HeadsetBattery",
                "BLUETOOTH_CONNECT permission not granted, skipping initialization"
            )
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun registerReceivers() {
        Log.d("HeadsetBattery", "Registering headset broadcast receivers")
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        Log.d("HeadsetBattery", "Receivers registered")
    }

    private fun connectHeadsetProfile() {
        Log.d("HeadsetBattery", "Connecting to headset profile proxy")
        bluetoothAdapter?.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    Log.d("HeadsetBattery", "Service connected, profile=$profile")
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = proxy as BluetoothHeadset
                        Log.d("HeadsetBattery", "Headset proxy connected, scanning devices...")
                        scanConnectedDevices()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    Log.d("HeadsetBattery", "Service disconnected, profile=$profile")
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProxy = null
                    }
                }
            },
            BluetoothProfile.HEADSET
        )
    }

    private fun scanConnectedDevices() {
        if (!hasBluetoothPermission()) {
            Log.w("HeadsetBattery", "No permission to scan devices")
            return
        }

        try {
            val connectedDevices = headsetProxy?.connectedDevices
            Log.d(
                "HeadsetBattery",
                "Scanning connected headset devices, found: ${connectedDevices?.size ?: 0}"
            )

            connectedDevices?.forEach { device ->
                try {
                    Log.d("HeadsetBattery", "Connected headset: ${device.name} (${device.address})")
                    updateDeviceBattery(device)
                } catch (e: SecurityException) {
                    Log.w("HeadsetBattery", "Permission denied accessing device: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.w("HeadsetBattery", "Permission denied while scanning devices: ${e.message}")
        }
    }

    private fun handleConnectionChange(intent: Intent) {
        val device = getDeviceFromIntent(intent)
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)

        Log.d("HeadsetBattery", "Connection state changed: device=${device?.address}, state=$state")

        device?.let {
            try {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("HeadsetBattery", "Headset connected: ${it.name}, updating battery")
                    updateDeviceBattery(it)
                } else {
                    Log.d("HeadsetBattery", "Headset state changed to $state for ${it.name}")
                }
            } catch (e: SecurityException) {
                Log.w("HeadsetBattery", "Permission denied handling connection: ${e.message}")
            }
        }
    }

    private fun handleBatteryChange(intent: Intent) {
        val device = getDeviceFromIntent(intent)
        Log.d("HeadsetBattery", "Battery change event for device: ${device?.address}")
        device?.let {
            try {
                Log.d("HeadsetBattery", "Updating battery for ${it.name}")
                updateDeviceBattery(it)
            } catch (e: SecurityException) {
                Log.w("HeadsetBattery", "Permission denied handling battery change: ${e.message}")
            }
        }
    }

    private fun updateDeviceBattery(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) {
            Log.w("HeadsetBattery", "No permission to update device battery")
            return
        }

        Log.d("HeadsetBattery", "Getting battery level for device: ${device.address}")
        val battery = getBatteryLevel(device)

        if (battery == null) {
            Log.w("HeadsetBattery", "Could not get battery level for ${device.address}")
            return
        }

        try {
            Log.d("HeadsetBattery", "${device.name}: $battery%")
        } catch (e: SecurityException) {
            Log.d("HeadsetBattery", "${device.address}: $battery%")
        }

        val deviceBattery = DeviceBattery(
            id = device.address,
            name = device.name ?: "Unknown",
            battery = battery.toFloat(),
            isCharging = false,
            iconRes = R.drawable.ic_bt_headset_hfp,
            paused = false,
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                putExtra("device_address", device.address)
            }
        )

        val existingDevice = repository.devices.value.find { it.id == device.address }
        if (existingDevice != null) {
            Log.d("HeadsetBattery", "Updating existing device in repository")
            repository.updateDevice(
                predicate = { it.id == device.address },
                updater = { deviceBattery }
            )
        } else {
            Log.d("HeadsetBattery", "Adding new device to repository")
            repository.addDevice(deviceBattery)
        }

        Log.d("HeadsetBattery", "Repository now has ${repository.devices.value.size} devices")
    }

    private fun getBatteryLevel(device: BluetoothDevice): Int? {
        if (!hasBluetoothPermission()) {
            Log.w("HeadsetBattery", "No permission to get battery level")
            return null
        }

        Log.d("HeadsetBattery", "Attempting to get battery level for ${device.address}")

        // try metadata api (android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Log.d("HeadsetBattery", "Trying metadata API (Android 9+)")
                val getMetadataMethod = BluetoothDevice::class.java.getMethod(
                    "getMetadata",
                    Int::class.java
                )
                val METADATA_MAIN_BATTERY = 1
                val battery = getMetadataMethod.invoke(device, METADATA_MAIN_BATTERY) as? String
                if (battery != null) {
                    Log.d("HeadsetBattery", "Got battery from metadata: $battery")
                    battery.toIntOrNull()?.let {
                        Log.d("HeadsetBattery", "Metadata battery parsed: $it")
                        return it
                    }
                } else {
                    Log.d("HeadsetBattery", "Metadata returned null")
                }
            } catch (e: SecurityException) {
                Log.w("HeadsetBattery", "Permission denied for metadata: ${e.message}")
                return null
            } catch (e: Exception) {
                Log.w("HeadsetBattery", "Metadata failed: ${e.message}")
            }
        }

        // fallback to getBatteryLevel
        try {
            Log.d("HeadsetBattery", "Trying getBatteryLevel() method")
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            Log.d("HeadsetBattery", "getBatteryLevel() returned: $level")
            if (level != null && level in 0..100) {
                Log.d("HeadsetBattery", "Valid battery level: $level")
                return level
            }
        } catch (e: SecurityException) {
            Log.w("HeadsetBattery", "Permission denied for battery level: ${e.message}")
        } catch (e: Exception) {
            Log.w("HeadsetBattery", "getBatteryLevel() failed: ${e.message}")
        }

        Log.w("HeadsetBattery", "All battery methods failed for ${device.address}")
        return null
    }

    private fun getDeviceFromIntent(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    fun cleanup() {
        Log.d("HeadsetBattery", "Cleaning up BluetoothHeadsetBatteryManager")
        try {
            context.unregisterReceiver(bluetoothReceiver)
            Log.d("HeadsetBattery", "Receivers unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w("HeadsetBattery", "Receiver was never registered (no permission)")
        }

        if (hasBluetoothPermission()) {
            headsetProxy?.let {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it)
                Log.d("HeadsetBattery", "Profile proxy closed")
            }
        }
        Log.d("HeadsetBattery", "Cleanup complete")
    }
}