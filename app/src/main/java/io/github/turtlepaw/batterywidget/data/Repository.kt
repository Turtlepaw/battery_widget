package io.github.turtlepaw.batterywidget.data

import android.content.Context

object Repository {
    private var instance: DeviceRepository? = null

    fun get(context: Context): DeviceRepository {
        return instance ?: synchronized(this) {
            instance ?: DeviceRepository(context.applicationContext).also {
                instance = it
            }
        }
    }

    fun cleanup() {
        instance?.cleanup()
        instance = null
    }
}