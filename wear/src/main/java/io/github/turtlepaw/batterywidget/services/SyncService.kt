package io.github.turtlepaw.batterywidget.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.shared.DataLayerConstants

class BatteryWidgetService : Service() {
    private lateinit var dataClient: DataClient
    private val widgetUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dataClient.sendData()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "battery_monitor")
            .setContentTitle("Battery Widget")
            .setContentText("Monitoring device battery")
            .setSmallIcon(R.drawable.ic_bolt)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        } else {
            startForeground(1, notification)
        }

        dataClient = Wearable.getDataClient(this)
        dataClient.sendData()

        // register for all battery/bt events
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(widgetUpdateReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "battery_monitor",
                "Battery monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps battery widget updated"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(widgetUpdateReceiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // restart if killed
    }
}

@SuppressLint("VisibleForTests")
fun DataClient.sendData(
    intent: Intent? = applicationContext.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
) {
    if (intent == null) {
        Log.d(
            "DeviceRepository",
            "Battery intent is null"
        )
        return
    }
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

    val putDataReq: PutDataRequest = PutDataMapRequest.create(DataLayerConstants.PATH).run {
        dataMap.run {
            putString(DataLayerConstants.DEVICE_NAME, Build.MODEL)
            putFloat(DataLayerConstants.BATTERY_LEVEL, level * 100f / scale)
            putBoolean(
                DataLayerConstants.BATTERY_CHARGING,
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            )
            putLong("time", System.currentTimeMillis())
        }
        setUrgent()
        asPutDataRequest()
    }

    putDataItem(putDataReq)

    Log.d(
        "DeviceRepository",
        "Wear battery updated: level=$level, scale=$scale, status=$status"
    )
}