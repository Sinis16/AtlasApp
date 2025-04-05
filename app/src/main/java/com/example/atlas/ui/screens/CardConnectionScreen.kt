package com.example.atlas.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.atlas.permissions.dispatcher.DispatcherEntry
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
    foundDevices: MutableList<BleDevice>
) {
    var isScanning by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var rationaleMessage by remember { mutableStateOf("") }

    // Configure PermissionManager
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

        if (foundDevices.isEmpty()) {
            Text(text = "No devices found", fontSize = 16.sp)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(foundDevices) { device ->
                    DeviceItem(device)
                }
            }
        }
    }

    // Rationale dialog
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

    // Manage scanning state
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
fun DeviceItem(device: BleDevice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text = "Address: ${device.address}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = "Name: ${device.name ?: "Unknown"}", fontSize = 14.sp)
        Text(text = "RSSI: ${device.rssi ?: "N/A"} dBm", fontSize = 14.sp)
    }
}