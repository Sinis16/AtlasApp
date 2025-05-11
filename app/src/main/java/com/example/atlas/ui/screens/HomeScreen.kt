package com.example.atlas.ui.screens

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.models.TagData
import com.example.atlas.ui.viewmodel.UserViewModel
import kotlinx.coroutines.delay
import java.util.UUID

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    navController: NavHostController,
    connectionStates: SnapshotStateMap<String, String>,
    foundDevices: MutableList<BleDevice>,
    deviceData: SnapshotStateMap<String, Map<String, String>>,
    connectionStartTimes: MutableMap<String, Long>,
    gattConnections: MutableMap<String, BluetoothGatt>,
    context: Context,
    lastReadRequestTimes: MutableMap<String, Long>,
    updateRate: MutableState<Long>,
    tagDataMap: SnapshotStateMap<String, TagData>,
    leaveBehindDistance: MutableState<Long>,
    isLeaveBehindEnabled: MutableState<Boolean>,
    isAdvancedMode: MutableState<Boolean>,
    viewModel: UserViewModel = hiltViewModel()
) {
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    // Load user data from clients table and validate session
    LaunchedEffect(Unit) {
        Log.d(TAG, "Checking user session")
        try {
            val hasSession = viewModel.checkUserSession()
            if (!hasSession) {
                Log.d(TAG, "No active session, navigating to logIn")
                navController.navigate("logIn") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                Log.d(TAG, "Triggering loadUserClientInfo")
                viewModel.loadUserClientInfo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session or loading user client info: ${e.localizedMessage}", e)
        }
    }

    // GATT reads for battery and RSSI
    LaunchedEffect(updateRate.value) {
        while (true) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val connectedDevices = connectionStates.filter { it.value == "Connected" }.keys
                val currentTime = System.currentTimeMillis()
                for (address in connectedDevices) {
                    gattConnections[address]?.let { gatt ->
                        lastReadRequestTimes[address] = currentTime
                        val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                        batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)?.let { char ->
                            gatt.readCharacteristic(char)
                        }
                        gatt.readRemoteRssi()
                    }
                }
            } else {
                Log.w(TAG, "BLUETOOTH_CONNECT permission denied, skipping GATT reads")
            }
            delay(updateRate.value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connected Devices",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val connectedDevices = connectionStates.filter { it.value == "Connected" }.keys
        if (connectedDevices.isEmpty()) {
            Text(
                text = "No devices connected",
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(connectedDevices.toList()) { address ->
                    val device = foundDevices.find { it.address == address }
                    var name = device?.name ?: "Unknown Device"
                    if (name == null) {
                        name = "Unknown Device"
                    }
                    val distance = deviceData[address]?.get("Distance")
                    val displayDistance = when {
                        distance == null || tagDataMap[address]?.distance == 0.0 -> "loading..."
                        else -> distance
                    }

                    // Parse distance for color interpolation (in cm)
                    val distanceValue = tagDataMap[address]?.distance ?: 0.0
                    // Interpolate between greyish red (0 cm) and greyish blue (1000 cm)
                    val fraction = (distanceValue / 700.0).coerceIn(0.0, 1.0).toFloat()
                    val greyishRed = Color(0xFF7A1515) // Muted red at 0 cm
                    val greyishBlue = Color(0xFF2C2C62) // Muted blue at 1000 cm
                    val interpolatedColor = lerp(greyishRed, greyishBlue, fraction)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (selectedDeviceAddress == address) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    interpolatedColor
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedDeviceAddress =
                                    if (selectedDeviceAddress == address) null else address
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = name, fontSize = 16.sp)
                            Text(text = displayDistance, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        selectedDeviceAddress?.let { address ->
            val device = foundDevices.find { it.address == address }
            var name = device?.name ?: "Unknown Device"
            if (name == null) {
                name = "Unknown Device"
            }
            val data = deviceData[address] ?: emptyMap()
            val tagData = tagDataMap[address]

            Text(
                text = "Details for $name",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            var tagId: String? = null
            val details = mutableListOf<String>()
            data.entries.forEach { (key, value) ->
                when (key) {
                    "Battery" -> details.add("Battery: $value")
                    "Distance" -> details.add("Distance: $value")
                    "RSSI" -> {
                        val rssiValue = value.removeSuffix(" dBm").toIntOrNull() ?: 0
                        val connectivity = if (rssiValue >= -70) "Strong" else "Weak"
                        val displayValue = if (isAdvancedMode.value) "$connectivity ($value)" else connectivity
                        details.add("BLE Connectivity: $displayValue")
                    }
                    "Latency" -> {
                        val latencyValue = value.removeSuffix(" ms").toLongOrNull() ?: 0
                        val speed = if (latencyValue <= 100) "Fast" else "Normal"
                        val displayValue = if (isAdvancedMode.value) "$speed ($value)" else speed
                        details.add("Speed: $displayValue")
                    }
                }
            }
            tagData?.let {
                Log.d(TAG, "Details for $address: id=${it.id}, distance=${it.distance}, battery=${it.battery}")
                tagId = it.id
                if (isAdvancedMode.value) {
                    details.add("Tag ID: ${it.id}")
                }
            } ?: run {
                Log.w(TAG, "No tagData for $address")
                tagId = address
                if (isAdvancedMode.value) {
                    details.add("Tag ID: $address")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    items(details) { detail ->
                        Text(
                            text = detail,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            tagId?.let { id ->
                                navController.navigate("tag/$id")
                                Log.d(TAG, "Navigating to TagScreen for tagId: $id")
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Buscar")
                    }
                }
            }
        }
    }
}