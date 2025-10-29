package io.github.turtlepaw.batterywidget.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.batterywidget.data.DeviceBattery
import io.github.turtlepaw.batterywidget.data.Repository
import io.github.turtlepaw.batterywidget.updateAllWidgets
import io.github.turtlepaw.shared.DataLayerConstants

private const val TAG = "DataLayerSample"
private const val START_ACTIVITY_PATH = "/start-activity"
private const val DATA_ITEM_RECEIVED_PATH = "/data-item-received"

class DataLayerListenerService : WearableListenerService() {
    private val repository by lazy {
        Repository.get(applicationContext)
    }

    fun getSamsungWearPackage(context: Context): String? {
        val pm = context.packageManager
        val installed = pm.getInstalledPackages(0).map { it.packageName }

        return installed.firstOrNull { it.startsWith("com.samsung.wearable") }
            ?: installed.firstOrNull { it == "com.samsung.android.heartplugin" }
            ?: installed.firstOrNull { it == "com.samsung.android.app.watchmanager" }
    }


    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(
            "DataLayerListenerService",
            "onDataChanged: ${dataEvents.count} events"
        )
        dataEvents.forEach {
            DataMapItem.fromDataItem(it.dataItem).dataMap.apply {
                val battery = getFloat(DataLayerConstants.BATTERY_LEVEL)
                val isCharging = getBoolean(DataLayerConstants.BATTERY_CHARGING)
                val deviceName = getString(DataLayerConstants.DEVICE_NAME)

                val intent = if (deviceName != null) {
                    val pm = applicationContext.packageManager
                    when {
                        deviceName.contains("pixel", ignoreCase = true) ->
                            pm.getLaunchIntentForPackage("com.google.android.apps.wear.companion")

                        deviceName.contains(
                            "galaxy",
                            ignoreCase = true
                        ) || deviceName.contains("SM", ignoreCase = true) -> {
                            val pkg = getSamsungWearPackage(applicationContext)
                            if (pkg != null) {
                                Intent().apply {
                                    setClassName(
                                        pkg,
                                        "com.samsung.android.waterplugin.maininfo.MainInfoActivity"
                                    )
                                }
                            } else null
                        }

                        else -> null
                    }
                } else null

                val device = DeviceBattery(
                    id = "watch_${it.dataItem.uri.host ?: "unknown"}",
                    name = deviceName ?: "Watch",
                    battery = battery,
                    isCharging = isCharging,
                    iconRes = if (deviceName?.contains("pixel") == true) R.drawable.ic_watch_pixel else R.drawable.ic_watch,
                    paused = false,
                    action = intent
                )

                // check if exists and update, otherwise add
                if (repository.devices.value.any { it.id == device.id }) {
                    repository.updateDevice(
                        predicate = { it.id == device.id },
                        updater = { device }
                    )
                } else {
                    repository.addDevice(device)
                }

                updateAllWidgets(applicationContext)

                Log.d(
                    "DataLayerListenerService",
                    "added device; ${repository.devices.value}"
                )
            }
        }
    }
}