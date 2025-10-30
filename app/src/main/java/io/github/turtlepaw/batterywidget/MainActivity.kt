package io.github.turtlepaw.batterywidget

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.github.turtlepaw.batterywidget.ui.theme.BatteryWidgetTheme
import io.github.turtlepaw.batterywidget.widgets.BatteryWidget
import io.github.turtlepaw.batterywidget.widgets.BatteryWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceIntent = Intent(this, BatteryWidgetService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            BatteryWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyList()
    }

    if (permissions.isEmpty()) {
        AppContent(
            allPermissionsGranted = true,
            onRequestPermissions = {},
            modifier = modifier
        )
    } else {
        val permissionsState = rememberMultiplePermissionsState(permissions)

        AppContent(
            allPermissionsGranted = permissionsState.allPermissionsGranted,
            onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
            modifier = modifier
        )
    }
}

@Composable
fun AppContent(
    allPermissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = rememberServiceRunning()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_bluetooth),
            contentDescription = "Battery Widget",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Battery Widget",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Monitor battery levels of your phone and connected Bluetooth devices",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!allPermissionsGranted) {
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Permissions Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This app needs Bluetooth and notification permissions to monitor your connected devices and display battery levels.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                            receiver = BatteryWidgetReceiver::class.java,
                            preview = BatteryWidget(),
                            previewState = DpSize(245.dp, 115.dp)
                        )
                    }
                }
            ) {
                Text(
                    "Add widget"
                )
            }

            Button(
                {
                    val intent = Intent(context, BatteryWidgetService::class.java)
                    context.startForegroundService(intent)
                },
                enabled = !isRunning
            ) {
                Text(
                    if (isRunning) "Running" else "Start"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    BatteryWidgetTheme {
        AppContent(
            allPermissionsGranted = false,
            onRequestPermissions = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenGrantedPreview() {
    BatteryWidgetTheme {
        AppContent(
            allPermissionsGranted = true,
            onRequestPermissions = {}
        )
    }
}

fun Context.isServiceRunning(): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == BatteryWidgetService::class.java.name }
}

@Composable
fun rememberServiceRunning(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceRunning by remember { mutableStateOf(context.isServiceRunning()) }


    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isServiceRunning = context.isServiceRunning()
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return isServiceRunning
}