package io.github.turtlepaw.batterywidget.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null) {
            val serviceIntent = Intent(context, BatteryWidgetService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}