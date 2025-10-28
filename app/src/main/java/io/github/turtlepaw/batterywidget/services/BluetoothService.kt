package io.github.turtlepaw.batterywidget.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.batterywidget.data.DeviceBattery
import io.github.turtlepaw.batterywidget.data.DeviceRepository
import java.util.UUID

class BluetoothLeBatteryManager(
    private val context: Context,
    private val repository: DeviceRepository
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()

    companion object {
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> handleConnected(intent)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDisconnected(intent)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                Log.d(
                    "LeBattery",
                    "Connection state change: ${gatt.device.name} status=$status newState=$newState"
                )
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(
                            "LeBattery",
                            "GATT connected: ${gatt.device.name}, discovering services..."
                        )
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("LeBattery", "GATT disconnected: ${gatt.device.name}")
                        gattConnections.remove(gatt.device.address)
                        gatt.close()
                    }
                }
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied in connection state change: ${e.message}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                Log.d("LeBattery", "Services discovered for ${gatt.device.name}, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("LeBattery", "Available services: ${gatt.services.map { it.uuid }}")
                    val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                    Log.d("LeBattery", "Battery service found: ${batteryService != null}")

                    val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                    Log.d("LeBattery", "Battery characteristic found: ${batteryChar != null}")

                    batteryChar?.let {
                        Log.d("LeBattery", "Reading battery characteristic for ${gatt.device.name}")
                        gatt.readCharacteristic(it)
                        gatt.setCharacteristicNotification(it, true)
                    }
                } else {
                    Log.w("LeBattery", "Service discovery failed with status: $status")
                }
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied in service discovery: ${e.message}")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            try {
                Log.d(
                    "LeBattery",
                    "Characteristic read for ${gatt.device.name}, status=$status, uuid=${characteristic.uuid}"
                )
                if (status == BluetoothGatt.GATT_SUCCESS &&
                    characteristic.uuid == BATTERY_LEVEL_CHAR_UUID
                ) {
                    val battery = characteristic.getIntValue(
                        BluetoothGattCharacteristic.FORMAT_UINT8, 0
                    )
                    Log.d("LeBattery", "Battery level read: ${battery}% for ${gatt.device.name}")
                    battery?.let { updateDeviceBattery(gatt.device, it) }
                } else {
                    Log.w("LeBattery", "Characteristic read failed or wrong UUID")
                }
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied reading characteristic: ${e.message}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                gatt.readCharacteristic(characteristic)
                val battery = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8, 0
                )
                battery?.let { updateDeviceBattery(gatt.device, it) }
            }
        }
    }

    init {
        Log.d("LeBattery", "Initializing BluetoothLeBatteryManager")
        Log.d("LeBattery", "Bluetooth adapter available: ${bluetoothAdapter != null}")
        registerReceivers()
        scanBondedDevices()
    }

    private fun registerReceivers() {
        Log.d("LeBattery", "Registering BLE broadcast receivers")
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    private fun scanBondedDevices() {
        val bondedDevices = bluetoothAdapter?.bondedDevices
        Log.d("LeBattery", "Scanning bonded devices, found: ${bondedDevices?.size ?: 0}")

        bondedDevices?.forEach { device ->
            try {
                Log.d(
                    "LeBattery",
                    "Bonded device: ${device.name} (${device.address}) type=${device.type}"
                )
                if (isLeDevice(device)) {
                    Log.d(
                        "LeBattery",
                        "Device is LE/Dual, attempting GATT connection: ${device.name}"
                    )
                    tryConnectGatt(device)
                } else {
                    Log.d("LeBattery", "Device is not LE, skipping: ${device.name}")
                }
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied while scanning device: ${e.message}")
            }
        }
    }

    private fun handleConnected(intent: Intent) {
        getDeviceFromIntent(intent)?.let { device ->
            try {
                Log.d("LeBattery", "Device connected: ${device.name} (${device.address})")
                if (isLeDevice(device)) {
                    Log.d("LeBattery", "Device is LE, attempting GATT connection")
                    tryConnectGatt(device)
                }
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied while handling connection: ${e.message}")
            }
        }
    }

    private fun handleDisconnected(intent: Intent) {
        getDeviceFromIntent(intent)?.let { device ->
            try {
                Log.d("LeBattery", "Device disconnected: ${device.name} (${device.address})")
                gattConnections[device.address]?.close()
                gattConnections.remove(device.address)
            } catch (e: SecurityException) {
                Log.w("LeBattery", "Permission denied while handling disconnection: ${e.message}")
            }
        }
    }

    private fun tryConnectGatt(device: BluetoothDevice) {
        if (gattConnections.containsKey(device.address)) {
            Log.d("LeBattery", "GATT already connected for ${device.address}")
            return
        }

        try {
            Log.d("LeBattery", "Connecting GATT to ${device.name} (${device.address})")
            val gatt = device.connectGatt(context, false, gattCallback)
            gattConnections[device.address] = gatt
            Log.d("LeBattery", "GATT connection initiated for ${device.address}")
        } catch (e: SecurityException) {
            Log.w("LeBattery", "Permission denied for GATT connect: ${e.message}")
        } catch (e: Exception) {
            Log.w("LeBattery", "GATT connect failed: ${e.message}")
        }
    }

    private fun updateDeviceBattery(device: BluetoothDevice, battery: Int) {
        try {
            Log.d("LeBattery", "Updating battery for ${device.name}: $battery%")

            val deviceBattery = DeviceBattery(
                id = device.address,
                name = device.name ?: "Unknown",
                battery = battery.toFloat(),
                isCharging = false,
                iconRes = R.drawable.ic_generic_tablet,
                paused = false
            )

            val existingDevice = repository.devices.value.find { it.id == device.address }
            if (existingDevice != null) {
                Log.d("LeBattery", "Updating existing device in repository")
                repository.updateDevice(
                    predicate = { it.id == device.address },
                    updater = { deviceBattery }
                )
            } else {
                Log.d("LeBattery", "Adding new device to repository")
                repository.addDevice(deviceBattery)
            }

            Log.d("LeBattery", "Repository now has ${repository.devices.value.size} devices")
        } catch (e: SecurityException) {
            Log.w("LeBattery", "Permission denied updating device battery: ${e.message}")
        }
    }

    private fun isLeDevice(device: BluetoothDevice): Boolean {
        return device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                device.type == BluetoothDevice.DEVICE_TYPE_DUAL
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
        Log.d("LeBattery", "Cleaning up BluetoothLeBatteryManager")
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("LeBattery", "Receiver was never registered")
        }
        gattConnections.values.forEach { it.close() }
        gattConnections.clear()
        Log.d("LeBattery", "Cleanup complete")
    }
}