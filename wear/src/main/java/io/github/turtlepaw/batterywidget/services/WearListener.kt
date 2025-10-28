package io.github.turtlepaw.batterywidget.services

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class DataLayerListenerService : WearableListenerService() {
    override fun onMessageReceived(message: MessageEvent) {
        Log.d(
            "DataLayerListenerService",
            "onMessageReceived: $message"
        )
        Wearable.getDataClient(applicationContext).sendData()
    }
}