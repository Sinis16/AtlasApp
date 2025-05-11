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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.models.TagData
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
    tagDataMap: SnapshotStateMap<String, TagData>
) {
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val FIRMWARE_VERSION_UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")

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
                        val deviceInfoService = gatt.getService(DEVICE_INFO_SERVICE_UUID)
                        deviceInfoService?.getCharacteristic(FIRMWARE_VERSION_UUID)?.let { char ->
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
                    val name = device?.name ?: "Unknown Device"
                    val battery = deviceData[address]?.get("Battery") ?: "N/A"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (selectedDeviceAddress == address) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
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
                            Text(text = "Battery: $battery", fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        selectedDeviceAddress?.let { address ->
            val device = foundDevices.find { it.address == address }
            val name = device?.name ?: "Unknown Device"
            val data = deviceData[address] ?: emptyMap()
            val tagData = tagDataMap[address]

            Text(
                text = "Details for $name",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                val details = mutableListOf<String>()
                // Add deviceData entries (e.g., Firmware, RSSI, Latency)
                data.entries.forEach { (key, value) ->
                    details.add("$key: $value")
                }
                // Add tagData fields with logging
                tagData?.let {
                    Log.d(TAG, "Details for $address: id=${it.id}, distance=${it.distance}, angle=${it.angle}, battery=${it.battery}")
                    details.add("Tag ID: ${it.id}")
                    details.add("Distance: ${String.format("%.2f", it.distance)} cm")
                    details.add("Angle: ${String.format("%.1f", it.angle)} deg")
                    details.add("Battery: ${it.battery}%")
                } ?: run {
                    // Fallback if tagData is null
                    Log.w(TAG, "No tagData for $address")
                    details.add("Tag ID: $address")
                    details.add("Distance: 0.00 cm")
                    details.add("Angle: 0.0 deg")
                    details.add("Battery: 0%")
                }
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
        }
    }
}