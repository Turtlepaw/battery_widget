package io.github.turtlepaw.batterywidget.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import io.github.turtlepaw.batterywidget.BatteryWidgetService
import io.github.turtlepaw.batterywidget.R
import io.github.turtlepaw.batterywidget.data.DeviceBattery
import io.github.turtlepaw.batterywidget.data.Repository
import io.github.turtlepaw.shared.DataLayerConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, BatteryWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, BatteryWidgetService::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Log.d("BatteryWidgetReceiver", "Received intent: ${intent.action}")

        // launch async work
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = Wearable.getCapabilityClient(context)
                    .getCapability("battery", CapabilityClient.FILTER_REACHABLE)
                    .await()

                info.nodes.forEach {
                    Wearable.getMessageClient(context)
                        .sendMessage(it.id, DataLayerConstants.PING_PATH, ByteArray(0))
                }
            } catch (e: Exception) {
                Log.e("BatteryWidgetReceiver", "Error sending ping", e)
            }
        }
    }
}

class BatteryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override var stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val repo = remember { Repository.get(context) }
                val devices by repo.devices.collectAsState()

                Layout(devices, context)
            }
        }
    }

    @OptIn(ExperimentalGlancePreviewApi::class)
    @Preview(widthDp = 500, heightDp = 150)
    @Composable
    private fun PreviewSingle() {
        Layout(
            listOf(
                DeviceBattery(
                    "emulator",
                    "Emulator",
                    93f,
                    true,
                    R.drawable.ic_generic_phone,
                    false
                )
            ),
            context = LocalContext.current
        )
    }

    @OptIn(ExperimentalGlancePreviewApi::class)
    @Preview(widthDp = 500, heightDp = 115)
    @Composable
    private fun PreviewMultiple() {
        Layout(
            listOf(
                DeviceBattery(
                    "phone",
                    "Phone",
                    93f,
                    false,
                    R.drawable.ic_settings_pixel_9,
                    false
                ),
                DeviceBattery(
                    "headset",
                    "Headset",
                    75f,
                    false,
                    R.drawable.ic_bt_headphones_a2dp,
                    false
                ),
            ),
            context = LocalContext.current
        )
    }

    @OptIn(ExperimentalGlancePreviewApi::class)
    @Preview(widthDp = 500, heightDp = 250)
    @Composable
    private fun PreviewLargeMultiple() {
        Layout(
            listOf(
                DeviceBattery(
                    "phone",
                    "Phone",
                    93f,
                    false,
                    R.drawable.ic_settings_pixel_9,
                    false
                ),
                DeviceBattery(
                    "watch",
                    "Watch",
                    42f,
                    true,
                    R.drawable.ic_watch,
                    false
                ),
                DeviceBattery(
                    "headset",
                    "Headset",
                    75f,
                    false,
                    R.drawable.ic_bt_headphones_a2dp,
                    false
                ),
            ),
            context = LocalContext.current
        )
    }

    @Composable
    private fun Layout(devices: List<DeviceBattery>, context: Context) {
        val size = LocalSize.current
        val heightDp = size.height.value
        Scaffold(
            backgroundColor = GlanceTheme.colors.widgetBackground,
            modifier = GlanceModifier.fillMaxSize(),
            horizontalPadding = 10.dp
        ) {
            if (heightDp > 120f) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(vertical = 10.dp),
                ) {
                    devices.take(3).forEachIndexed { index, device ->
                        Device(
                            device,
                            context,
                            devices.size,
                            index,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        if (index < devices.take(3).size - 1) {
                            Spacer(modifier = GlanceModifier.size(6.dp))
                        }
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    devices.take(3).forEachIndexed { index, device ->
                        Device(
                            device,
                            context,
                            devices.size,
                            index,
                            modifier = GlanceModifier.defaultWeight().padding(vertical = 10.dp)
                        )
                        if (index < devices.take(3).size - 1) {
                            Spacer(modifier = GlanceModifier.size(5.dp))
                        }
                    }
                }
            }
        }
    }


    fun isNightMode(context: Context): Boolean {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun Device(
        device: DeviceBattery,
        context: Context,
        deviceCount: Int,
        index: Int,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val nightMode = isNightMode(context)
        val baseColor = when (index) {
            0 -> GlanceTheme.colors.primaryContainer
            1 -> GlanceTheme.colors.tertiaryContainer
            2 -> GlanceTheme.colors.secondaryContainer
            else -> GlanceTheme.colors.primaryContainer
        }.run {
            if (nightMode)
                this
            else ColorProvider(
                lerp(
                    lerp(
                        this.getColor(context),
                        GlanceTheme.colors.primary.getColor(context),
                        0.15f
                    ),
                    Color.White,
                    0.05f
                )
            )
        }
        val onBaseColor = when (index) {
            0 -> GlanceTheme.colors.onPrimaryContainer
            1 -> GlanceTheme.colors.onTertiaryContainer
            2 -> GlanceTheme.colors.onSecondaryContainer
            else -> GlanceTheme.colors.onPrimaryContainer
        }

        val startTextColor = ColorProvider(
            onBaseColor.getColor(context).copy(0.8f)
        )

        val bgColor = ColorProvider(
            lerp(
                if (nightMode) Color.Black else Color.White,
                baseColor.getColor(context),
                if (nightMode) 0.6f else 0.8f
            )
        )

        Box(
            modifier = modifier
                .cornerRadius(16.dp),
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .run {
                        if (device.action != null) clickable(
                            {
                                context.startActivity(device.action.apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                })
                            })
                        else this
                    }
                    .cornerRadius(16.dp),
            ) {
                LinearProgressIndicator(
                    progress = device.battery / 100f,
                    color = baseColor.run {
                        if (nightMode)
                            this
                        else ColorProvider(
                            lerp(
                                this.getColor(context),
                                Color.Black,
                                0.1f
                            )
                        )
                    },
                    backgroundColor = bgColor,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                )
                Row(
                    modifier = GlanceModifier.fillMaxSize()
                        .padding(horizontal = if (deviceCount > 1) 12.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        provider = ImageProvider(device.iconRes),
                        contentDescription = "Device Icon",
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(
                            startTextColor
                        )
                    )

                    if (deviceCount == 1) {
                        Spacer(modifier = GlanceModifier.size(8.dp))
                        Text(
                            device.name,
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                color = startTextColor
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (deviceCount > 1) {
                        Spacer(modifier = GlanceModifier.size(4.dp))
                    }

                    if (!(device.isCharging || device.paused) || device.id.contains("watch")) {
                        Text(
                            text = "${device.battery.toInt()}%",
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                color = onBaseColor
                            )
                        )

                        if (device.id.contains("watch") && (device.isCharging || device.paused)) {
                            Spacer(
                                modifier = GlanceModifier.size(12.dp)
                            )
                        }
                    }

                    if (device.isCharging || device.paused) {
                        Box(
                            modifier = GlanceModifier.background(
                                onBaseColor
                            ).cornerRadius(50.dp).size(19.dp)
                        ) {
                            Image(
                                provider = ImageProvider(
                                    if (device.paused) R.drawable.ic_shield else R.drawable.ic_bolt
                                ),
                                modifier = GlanceModifier.padding(
                                    if (device.paused) 2.5.dp else 4.dp
                                ),
                                contentDescription = if (device.paused) "Shield Icon" else "Charging Icon",
                                colorFilter = ColorFilter.tint(
                                    bgColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
