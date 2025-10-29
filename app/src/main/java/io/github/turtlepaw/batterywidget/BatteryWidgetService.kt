package io.github.turtlepaw.batterywidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.turtlepaw.batterywidget.data.DeviceRepository
import io.github.turtlepaw.batterywidget.data.Repository
import io.github.turtlepaw.batterywidget.widgets.BatteryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryWidgetService : Service() {
    private lateinit var repository: DeviceRepository
    private val widgetUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // update widgets on any battery/bt change
            updateAllWidgets(context)
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
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        } else {
            startForeground(1, notification)
        }

        repository = Repository.get(applicationContext)

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
        repository.cleanup()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // restart if killed
    }
}

fun updateAllWidgets(context: Context) {
    CoroutineScope(Dispatchers.Main).launch {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(BatteryWidget::class.java)
        glanceIds.forEach {
            BatteryWidget().update(context, it)
        }
    }
}