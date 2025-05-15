package com.example.atlas.ui.screens

import android.annotation.SuppressLint
import android.os.Build
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.atlas.MainActivity
import com.example.atlas.blescanner.BleScanManager
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.models.Tracker
import com.example.atlas.models.User
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.permissions.dispatcher.dsl.checkPermissions
import com.example.atlas.permissions.dispatcher.dsl.doOnDenied
import com.example.atlas.permissions.dispatcher.dsl.doOnGranted
import com.example.atlas.permissions.dispatcher.dsl.rationale
import com.example.atlas.permissions.dispatcher.dsl.withRequestCode
import com.example.atlas.ui.viewmodel.TrackerViewModel
import com.example.atlas.ui.viewmodel.UserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.util.Log

@SuppressLint("UnrememberedMutableState")
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
    onDisconnect: (String) -> Unit,
    trackerViewModel: TrackerViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    var isScanning by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var rationaleMessage by remember { mutableStateOf("") }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val user by userViewModel.user.collectAsStateWithLifecycle()
    val isAuthenticated by userViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Fetch trackers for all found devices
    val deviceAddresses = foundDevices.map { it.address }
    val trackers by trackerViewModel.getTrackersByBleIds(deviceAddresses)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val trackerMap by remember(trackers) {
        derivedStateOf { trackers.associateBy { it.ble_id } }
    }

    // Log tracker names
    LaunchedEffect(trackers) {
        trackers.forEach { tracker ->
            Log.d("CardConnectionScreen", "Tracker BLE ID: ${tracker.ble_id}, Name: ${tracker.name}")
        }
    }

    // Check session on entry
    LaunchedEffect(Unit) {
        Log.d("CardConnectionScreen", "Initial isAuthenticated: $isAuthenticated")
        try {
            val hasSession = userViewModel.checkUserSession()
            Log.d("CardConnectionScreen", "Session check result: hasSession=$hasSession")
            if (!hasSession) {
                Log.d("CardConnectionScreen", "No active session, navigating to logIn")
                navController.navigate("logIn") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } catch (e: Exception) {
            Log.e("CardConnectionScreen", "Error checking session: ${e.localizedMessage}", e)
        }
    }

    // Track if a Proton device is connected
    val isProtonConnected by derivedStateOf {
        connectionStates.any { (address, state) ->
            state == "Connected" && foundDevices.find { it.address == address }?.name?.startsWith("Proton") == true
        }
    }

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
        // Show connection error
        connectionError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

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
                val device = foundDevices.find { it.address == address }
                val tracker = trackerMap[address]
                val displayName = tracker?.name ?: device?.name ?: "Unknown Device"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayName,
                        fontSize = 14.sp
                    )
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
                    permissionManager.checkRequestAndDispatch(1)
                    connectionError = null
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
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }

        // Filter devices into Proton and Electron lists
        val protonDevices = foundDevices.filter { device ->
            device.name != null &&
                    device.name != "Unknown" &&
                    device.name.startsWith("Proton")
        }
        val electronDevices = foundDevices.filter { device ->
            device.name != null &&
                    device.name != "Unknown" &&
                    device.name.startsWith("Electron") &&
                    isProtonConnected
        }

        // Detected Protons section
        Text(
            text = "Detected Protons:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
        )
        if (protonDevices.isEmpty()) {
            Text(
                text = "No Proton devices found",
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(protonDevices) { device ->
                    DeviceItem(
                        device = device,
                        connectionState = connectionStates[device.address] ?: "Disconnected",
                        isSavedDevice = device.address == savedDeviceAddress.value,
                        user = user,
                        trackerMap = trackerMap,
                        trackerViewModel = trackerViewModel,
                        coroutineScope = coroutineScope,
                        setConnectionError = { connectionError = it },
                        onConnect = onConnect
                    )
                }
            }
        }

        // Detected Electrons section (only if Proton is connected)
        if (isProtonConnected) {
            Text(
                text = "Detected Electrons:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )
            if (electronDevices.isEmpty()) {
                Text(
                    text = "No Electron devices found",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(electronDevices) { device ->
                        DeviceItem(
                            device = device,
                            connectionState = connectionStates[device.address] ?: "Disconnected",
                            isSavedDevice = device.address == savedDeviceAddress.value,
                            user = user,
                            trackerMap = trackerMap,
                            trackerViewModel = trackerViewModel,
                            coroutineScope = coroutineScope,
                            setConnectionError = { connectionError = it },
                            onConnect = onConnect
                        )
                    }
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
            val connectedAddresses = connectionStates.filter { it.value == "Connected" }.keys
            foundDevices.retainAll { device -> connectedAddresses.contains(device.address) }
            Log.d("CardConnectionScreen", "Scan started, retained connected devices: $connectedAddresses")
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
    user: User?,
    trackerMap: Map<String, Tracker>,
    trackerViewModel: TrackerViewModel,
    coroutineScope: CoroutineScope,
    setConnectionError: (String) -> Unit,
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
            val displayName = trackerMap[device.address]?.name ?: device.name ?: "Unknown"
            Text(
                text = displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Address: ${device.address}",
                fontSize = 14.sp
            )
            Text(
                text = "RSSI: ${device.rssi ?: "N/A"} dBm",
                fontSize = 14.sp
            )
            Text(
                text = "Status: $connectionState" + if (isSavedDevice) " (Saved)" else "",
                fontSize = 14.sp
            )
        }
        Button(
            onClick = {
                coroutineScope.launch {
                    val userId = user?.id
                    if (userId == null) {
                        Log.w("CardConnectionScreen", "No user ID available")
                        setConnectionError("Please log in to connect")
                        return@launch
                    }

                    trackerViewModel.getTrackerByBleId(device.address)
                    val tracker = trackerViewModel.selectedTracker.value
                    if (tracker == null) {
                        Log.d("CardConnectionScreen", "No tracker found for ble_id: ${device.address}, proceeding to connect")
                        onConnect(device.address)
                        return@launch
                    }

                    val authorized = listOfNotNull(
                        tracker.user1,
                        tracker.user2,
                        tracker.user3
                    ).contains(userId)

                    if (authorized) {
                        Log.d("CardConnectionScreen", "User $userId authorized for tracker ${device.address}, connecting")
                        onConnect(device.address)
                    } else {
                        Log.w("CardConnectionScreen", "User $userId not authorized for tracker ${device.address}")
                        setConnectionError("This tracker doesn’t belong to you")
                    }
                }
            },
            enabled = connectionState != "Connecting" && connectionState != "Connected"
        ) {
            Text("Connect")
        }
    }
}