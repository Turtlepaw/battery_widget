package io.github.turtlepaw.batterywidget.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.turtlepaw.batterywidget.BatteryWidgetService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(
            "BootReceiver",
            "Received intent: ${intent?.action}"
        )
        if (context != null) {
            val serviceIntent = Intent(context, BatteryWidgetService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}