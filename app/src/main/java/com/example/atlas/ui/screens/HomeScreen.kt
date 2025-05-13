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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    var notificationDeviceAddress by remember { mutableStateOf<String?>(null) }
    val notificationState = remember { mutableStateMapOf<String, NotificationStatus>() }

    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

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

    LaunchedEffect(tagDataMap, connectionStates, leaveBehindDistance.value, isLeaveBehindEnabled.value) {
        while (true) {
            val connectedDevices = connectionStates.filter { it.value == "Connected" }.keys
            for (address in connectedDevices) {
                val deviceId = deviceData[address]?.get("DeviceID")
                if (deviceId?.startsWith("UWB_TX_") != true) continue // Only process TX devices for notifications
                val distance = tagDataMap[address]?.distance ?: 0.0
                val currentStatus = notificationState[address] ?: NotificationStatus.None
                val threshold = leaveBehindDistance.value.toDouble()

                Log.d(TAG, "Checking notification for $address: distance=$distance cm, threshold=$threshold cm, status=$currentStatus")
                when {
                    distance > threshold && currentStatus == NotificationStatus.None -> {
                        notificationDeviceAddress = address
                        notificationState[address] = NotificationStatus.Shown
                        Log.d(TAG, "Showing notification for $address: distance=$distance cm, threshold=$threshold cm")
                    }
                    distance <= threshold && (currentStatus == NotificationStatus.Ignored || currentStatus == NotificationStatus.Shown) -> {
                        notificationState[address] = NotificationStatus.None
                        if (notificationDeviceAddress == address) {
                            notificationDeviceAddress = null
                        }
                        Log.d(TAG, "Reset notification state for $address: distance=$distance cm, threshold=$threshold cm")
                    }
                    distance > threshold && currentStatus is NotificationStatus.PendingRetrigger -> {
                        val pendingTime = currentStatus.timestamp
                        if (System.currentTimeMillis() - pendingTime >= 10_000) {
                            notificationDeviceAddress = address
                            notificationState[address] = NotificationStatus.Shown
                            Log.d(TAG, "Re-triggering notification for $address: distance=$distance cm, threshold=$threshold cm")
                        }
                    }
                }
            }
            delay(1000)
        }
    }

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
                            Log.d(TAG, "Initiated battery read for $address")
                        }
                        gatt.readRemoteRssi()
                        Log.d(TAG, "Initiated RSSI read for $address")
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
            text = "Connected Receivers",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val rxDevices = connectionStates.entries.filter { (address: String, state: String) ->
            state == "Connected" && deviceData[address]?.get("DeviceID")?.startsWith("UWB_TX_") != true
        }.map { it.key }
        if (rxDevices.isEmpty()) {
            Text(
                text = "No receivers connected",
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Log.d(TAG, "Displaying ${rxDevices.size} RX devices: $rxDevices")
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(rxDevices) { address ->
                    val device = foundDevices.find { it.address == address }
                    val deviceId = deviceData[address]?.get("DeviceID")
                    val name = device?.name ?: deviceId ?: "Unknown Receiver"
                    val displayName = "$name (RX)"

                    val greyishBlue = Color(0xFF2C2C62)
                    val isSelected = selectedDeviceAddress == address

                    Log.d(TAG, "Rendering RX device $address: name=$displayName")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    greyishBlue
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedDeviceAddress = if (isSelected) null else address
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = displayName, fontSize = 16.sp)
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            // RX Details
                            val data = deviceData[address] ?: emptyMap()
                            val tagData = tagDataMap[address]
                            var tagId: String? = null
                            val details = mutableListOf<String>()
                            data.entries.forEach { (key, value) ->
                                when (key) {
                                    "Battery" -> details.add("Battery: $value")
                                    "DeviceID" -> if (isAdvancedMode.value) details.add("Device ID: $value")
                                    "ReceivedTxId" -> if (isAdvancedMode.value) details.add("Received TX ID: $value")
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
                                Log.d(TAG, "Details for RX $address: id=${it.id}, battery=${it.battery}")
                                tagId = it.id
                                if (isAdvancedMode.value) {
                                    details.add("Tag ID: ${it.id}")
                                }
                            } ?: run {
                                Log.w(TAG, "No tagData for RX $address")
                                tagId = address
                                if (isAdvancedMode.value) {
                                    details.add("Tag ID: $address")
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                            ) {
                                items(details) { detail ->
                                    Text(
                                        text = detail,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Related Transmitters",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            // Find related TX devices
                            val receivedTxId = deviceData[address]?.get("ReceivedTxId")
                            val txDevices = if (receivedTxId != null) {
                                connectionStates.entries.filter { (txAddress: String, state: String) ->
                                    state == "Connected" && deviceData[txAddress]?.get("DeviceID") == "UWB_TX_$receivedTxId"
                                }.map { it.key }
                            } else {
                                emptyList()
                            }

                            if (txDevices.isEmpty()) {
                                Text(
                                    text = "No related transmitters found",
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Log.d(TAG, "Found ${txDevices.size} related TX devices for RX $address: $txDevices")
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                ) {
                                    items(txDevices) { txAddress ->
                                        val txDevice = foundDevices.find { it.address == txAddress }
                                        val txDeviceId = deviceData[txAddress]?.get("DeviceID")
                                        val txName = txDevice?.name ?: txDeviceId ?: "Unknown Transmitter"
                                        val displayTxName = "$txName (TX)"
                                        val distance = deviceData[txAddress]?.get("Distance") ?: "No distance"
                                        val distanceValue = tagDataMap[txAddress]?.distance ?: 0.0
                                        val fraction = (distanceValue / 700.0).coerceIn(0.0, 1.0).toFloat()
                                        val greyishRed = Color(0xFF7A1515)
                                        val greyishBlue = Color(0xFF2C2C62)
                                        val interpolatedColor = lerp(greyishRed, greyishBlue, fraction)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    color = interpolatedColor,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = displayTxName, fontSize = 14.sp)
                                                Text(text = distance, fontSize = 14.sp)
                                            }
                                        }

                                        // TX Details
                                        val txData = deviceData[txAddress] ?: emptyMap()
                                        val txTagData = tagDataMap[txAddress]
                                        val txDetails = mutableListOf<String>()
                                        var txTagId: String? = null
                                        txData.entries.forEach { (key, value) ->
                                            when (key) {
                                                "Battery" -> txDetails.add("Battery: $value")
                                                "Distance" -> txDetails.add("Distance: $value")
                                                "DeviceID" -> if (isAdvancedMode.value) txDetails.add("Device ID: $value")
                                                "RSSI" -> {
                                                    val rssiValue = value.removeSuffix(" dBm").toIntOrNull() ?: 0
                                                    val connectivity = if (rssiValue >= -70) "Strong" else "Weak"
                                                    val displayValue = if (isAdvancedMode.value) "$connectivity ($value)" else connectivity
                                                    txDetails.add("BLE Connectivity: $displayValue")
                                                }
                                                "Latency" -> {
                                                    val latencyValue = value.removeSuffix(" ms").toLongOrNull() ?: 0
                                                    val speed = if (latencyValue <= 100) "Fast" else "Normal"
                                                    val displayValue = if (isAdvancedMode.value) "$speed ($value)" else speed
                                                    txDetails.add("Speed: $displayValue")
                                                }
                                            }
                                        }
                                        txTagData?.let {
                                            Log.d(TAG, "Details for TX $txAddress: id=${it.id}, distance=${it.distance}, battery=${it.battery}")
                                            txTagId = it.id
                                            if (isAdvancedMode.value) {
                                                txDetails.add("Tag ID: ${it.id}")
                                            }
                                        } ?: run {
                                            Log.w(TAG, "No tagData for TX $txAddress")
                                            txTagId = txAddress
                                            if (isAdvancedMode.value) {
                                                txDetails.add("Tag ID: $txAddress")
                                            }
                                        }

                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp)
                                                .heightIn(max = 100.dp)
                                        ) {
                                            items(txDetails) { detail ->
                                                Text(
                                                    text = detail,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                txTagId?.let { id ->
                                                    navController.navigate("tag/$id")
                                                    Log.d(TAG, "Navigating to TagScreen for TX tagId: $id")
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text("Buscar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    notificationDeviceAddress?.let { address ->
        val device = foundDevices.find { it.address == address }
        val deviceId = deviceData[address]?.get("DeviceID")
        val name = device?.name ?: deviceId ?: "Unknown Device"
        val tagData = tagDataMap[address]
        val tagId = tagData?.id ?: address

        Dialog(
            onDismissRequest = {
                notificationDeviceAddress = null
                notificationState[address] = NotificationStatus.PendingRetrigger(System.currentTimeMillis())
                Log.d(TAG, "Notification closed for $address, pending re-trigger after 10s")
            }
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Objeto fuera de rango",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = {
                                notificationDeviceAddress = null
                                notificationState[address] = NotificationStatus.PendingRetrigger(System.currentTimeMillis())
                                Log.d(TAG, "X button clicked for $address, pending re-trigger after 10s")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estás dejando atrás el objeto $name",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                notificationDeviceAddress = null
                                notificationState[address] = NotificationStatus.Ignored
                                Log.d(TAG, "Ignorar clicked for $address, suppressing notification")
                            }
                        ) {
                            Text("Ignorar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                navController.navigate("tag/$tagId")
                                notificationDeviceAddress = null
                                notificationState[address] = NotificationStatus.None
                                Log.d(TAG, "Buscar clicked for $address, navigating to TagScreen")
                            }
                        ) {
                            Text("Buscar")
                        }
                    }
                }
            }
        }
    }
}

private sealed class NotificationStatus {
    object None : NotificationStatus()
    object Shown : NotificationStatus()
    object Ignored : NotificationStatus()
    data class PendingRetrigger(val timestamp: Long) : NotificationStatus()
}