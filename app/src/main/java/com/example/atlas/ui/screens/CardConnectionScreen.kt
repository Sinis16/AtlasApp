package com.example.atlas.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.atlas.MainActivity
import com.example.atlas.blescanner.BleScanManager
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.permissions.dispatcher.dsl.checkPermissions
import com.example.atlas.permissions.dispatcher.dsl.doOnDenied
import com.example.atlas.permissions.dispatcher.dsl.doOnGranted
import com.example.atlas.permissions.dispatcher.dsl.rationale
import com.example.atlas.permissions.dispatcher.dsl.withRequestCode

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun CardConnectionScreen(
    navController: NavHostController,
    permissionManager: PermissionManager,
    bleScanManager: BleScanManager,
    foundDevices: MutableList<BleDevice>,
    connectionStates: SnapshotStateMap<String, String>,
    savedDeviceAddress: MutableState<String?>,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var rationaleMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        permissionManager.buildRequestResultsDispatcher {
            withRequestCode(1) {
                checkPermissions(MainActivity.BLE_PERMISSIONS)
                rationale { _, _ ->
                    rationaleMessage = "Bluetooth permissions are required to scan devices"
                    showRationaleDialog = true
                }
                doOnGranted {
                    bleScanManager.scanBleDevices()
                    Log.d("CardConnectionScreen", "Permissions granted, scanning started")
                }
                doOnDenied {
                    Log.d("CardConnectionScreen", "Permissions denied")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Show connected devices with Disconnect button
        val connectedDevices = connectionStates.filter { it.value == "Connected" }.keys
        LaunchedEffect(connectionStates) {
            Log.d("CardConnectionScreen", "connectionStates changed: $connectionStates")
            Log.d("CardConnectionScreen", "Connected devices: $connectedDevices")
        }
        if (connectedDevices.isNotEmpty()) {
            Text(
                text = "Connected Devices:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            connectedDevices.forEach { address ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Text(
                            text = address,
                            fontSize = 14.sp
                        )
                        foundDevices.find { it.address == address }?.name?.let { name ->
                            Text(
                                text = " ($name)",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Button(
                        onClick = { onDisconnect(address) },
                        modifier = Modifier.size(width = 100.dp, height = 36.dp)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        } else {
            Text(
                text = "No devices connected",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Start Scan button
        Button(
            onClick = {
                if (!isScanning) {
                    permissionManager checkRequestAndDispatch 1
                }
            },
            enabled = !isScanning,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(text = if (isScanning) "Scanning..." else "Start Scan")
        }

        // Show saved device status
        savedDeviceAddress.value?.let { address ->
            Text(
                text = "Last Connected Device: $address",
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (foundDevices.isEmpty()) {
            Text(text = "No devices found", fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(foundDevices) { device ->
                    DeviceItem(
                        device = device,
                        connectionState = connectionStates[device.address] ?: "Disconnected",
                        isSavedDevice = device.address == savedDeviceAddress.value,
                        onConnect = onConnect
                    )
                }
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Permission Required") },
            text = { Text(rationaleMessage) },
            confirmButton = {
                TextButton(onClick = {
                    permissionManager.checkRequestAndDispatch(1, comingFromRationale = true)
                    showRationaleDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(bleScanManager) {
        bleScanManager.beforeScanActions.clear()
        bleScanManager.afterScanActions.clear()
        bleScanManager.beforeScanActions.add {
            isScanning = true
            foundDevices.clear()
            Log.d("CardConnectionScreen", "Scan started")
        }
        bleScanManager.afterScanActions.add {
            isScanning = false
            Log.d("CardConnectionScreen", "Scan stopped")
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    connectionState: String,
    isSavedDevice: Boolean,
    onConnect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Address: ${device.address}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = "Name: ${device.name ?: "Unknown"}", fontSize = 14.sp)
            Text(text = "RSSI: ${device.rssi ?: "N/A"} dBm", fontSize = 14.sp)
            Text(text = "Status: $connectionState" + if (isSavedDevice) " (Saved)" else "", fontSize = 14.sp)
        }
        Button(
            onClick = { onConnect(device.address) },
            enabled = connectionState != "Connecting" && connectionState != "Connected"
        ) {
            Text("Connect")
        }
    }
}