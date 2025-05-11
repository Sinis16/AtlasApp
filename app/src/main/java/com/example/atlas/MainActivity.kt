package com.example.atlas

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.atlas.blescanner.BleScanManager
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.blescanner.model.BleScanCallback
import com.example.atlas.models.TagData
import com.example.atlas.navigation.AppNavHost
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.ui.components.BottomNavBar
import com.example.atlas.ui.components.TopNavBar
import com.example.atlas.ui.theme.AtlasTheme
import androidx.compose.ui.platform.LocalContext
import java.nio.ByteBuffer
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    private lateinit var btManager: BluetoothManager
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val DISTANCE_SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
    private val DISTANCE_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-567812345679")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val subscriptionRetries = mutableMapOf<String, Int>()
    private val connectionRetries = mutableMapOf<String, Int>()
    private val gattOperationQueue = mutableListOf<() -> Unit>()
    private var isProcessingGattOperation = false
    private val isReconnectingForDistance = mutableMapOf<String, Boolean>()
    private val reconnectAttempts = mutableMapOf<String, Int>()

    private fun enqueueGattOperation(operation: () -> Unit) {
        gattOperationQueue.add(operation)
        processNextGattOperation()
    }

    private fun processNextGattOperation() {
        if (isProcessingGattOperation || gattOperationQueue.isEmpty()) return
        isProcessingGattOperation = true
        val operation = gattOperationQueue.removeAt(0)
        operation()
    }

    private fun completeGattOperation() {
        isProcessingGattOperation = false
        processNextGattOperation()
    }

    private fun hasValidDistance(
        address: String,
        deviceData: SnapshotStateMap<String, Map<String, String>>,
        tagDataMap: SnapshotStateMap<String, TagData>
    ): Boolean {
        val distanceStr = deviceData[address]?.get("Distance")
        val distanceCm = distanceStr?.replace(" cm", "")?.toDoubleOrNull() ?: tagDataMap[address]?.distance ?: 0.0
        return distanceCm > 0
    }

    private fun startReconnectionCycle(
        address: String,
        gattConnections: MutableMap<String, BluetoothGatt>,
        connectionStates: SnapshotStateMap<String, String>,
        deviceData: SnapshotStateMap<String, Map<String, String>>,
        tagDataMap: SnapshotStateMap<String, TagData>,
        gattCallback: BluetoothGattCallback
    ) {
        if (isReconnectingForDistance[address] == true) return
        isReconnectingForDistance[address] = true
        val attempts = reconnectAttempts.getOrDefault(address, 0)
        if (attempts >= 6) {
            Log.e(TAG, "Max reconnection attempts reached for $address")
            isReconnectingForDistance.remove(address)
            reconnectAttempts.remove(address)
            return
        }
        reconnectAttempts[address] = attempts + 1
        Log.d(TAG, "Starting reconnection cycle for $address, attempt ${attempts + 1}/6")

        Handler(Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isReconnectingForDistance[address] == true && !hasValidDistance(address, deviceData, tagDataMap)) {
                    gattConnections[address]?.let { gatt ->
                        Log.d(TAG, "Disconnecting $address for reconnection cycle")
                        gatt.disconnect()
                        gatt.close()
                        gattConnections.remove(address)
                    }
                    val device = btManager.adapter?.getRemoteDevice(address)
                    if (device != null) {
                        val newGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                        gattConnections[address] = newGatt
                        Log.d(TAG, "Reconnection attempt ${attempts + 1}/6 for $address")
                        startReconnectionCycle(address, gattConnections, connectionStates, deviceData, tagDataMap, gattCallback)
                    } else {
                        Log.e(TAG, "Device not found for $address, stopping reconnection")
                        isReconnectingForDistance.remove(address)
                        reconnectAttempts.remove(address)
                    }
                } else {
                    Log.d(TAG, "Stopping reconnection cycle for $address: ${if (hasValidDistance(address, deviceData, tagDataMap)) "distance received" else "cycle cancelled"}")
                    isReconnectingForDistance.remove(address)
                    reconnectAttempts.remove(address)
                }
            } else {
                Log.w(TAG, "BLUETOOTH_CONNECT permission missing, stopping reconnection for $address")
                isReconnectingForDistance.remove(address)
                reconnectAttempts.remove(address)
            }
        }, 5000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: return
            Log.d(TAG, "Gatt callback for $address: status=$status, newState=$newState")
            val isReconnecting = isReconnectingForDistance[address] == true

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to $address")
                    if (!isReconnecting) {
                        connectionStates[address] = "Connected"
                    }
                    getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                        .edit().putString("connectedDevice", address).apply()
                    connectionStartTimes[address] = System.currentTimeMillis()
                    subscriptionRetries[address] = 0
                    connectionRetries.remove(address)
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d(TAG, "Requesting service discovery for $address")
                        enqueueGattOperation {
                            gatt.discoverServices()
                        }
                        // Start reconnection cycle if no distance
                        if (!hasValidDistance(address, deviceData, tagDataMap)) {
                            startReconnectionCycle(address, gattConnections, connectionStates, deviceData, tagDataMap, this)
                        }
                    } else {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission missing for $address")
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from $address")
                    if (!isReconnecting) {
                        connectionStates[address] = "Disconnected"
                        gattConnections.remove(address)?.close()
                        deviceData.remove(address)
                        tagDataMap.remove(address)
                        connectionStartTimes.remove(address)
                        lastReadRequestTimes.remove(address)
                        subscriptionRetries.remove(address)
                        if (savedDeviceAddress.value == address) {
                            getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                                .edit().remove("connectedDevice").apply()
                            savedDeviceAddress.value = null
                        }
                    }
                    if (status == 133 || status == 147) {
                        val retries = connectionRetries.getOrDefault(address, 0) + 1
                        if (retries < 3) {
                            Log.d(TAG, "Connection error (status=$status) for $address, retrying ($retries/3)")
                            connectionRetries[address] = retries
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    try {
                                        val refreshMethod = gatt?.javaClass?.getMethod("refresh")
                                        if (refreshMethod?.invoke(gatt) as? Boolean == true) {
                                            Log.d(TAG, "GATT cache refreshed for $address")
                                        } else {
                                            Log.w(TAG, "GATT cache refresh failed for $address")
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "GATT cache refresh not supported: ${e.message}")
                                    }
                                    gattConnections[address]?.disconnect()
                                    gattConnections[address]?.close()
                                    gattConnections.remove(address)
                                    val device = btManager.adapter?.getRemoteDevice(address)
                                    if (device != null) {
                                        val newGatt = device.connectGatt(this@MainActivity, false, this)
                                        gattConnections[address] = newGatt
                                        if (!isReconnecting) {
                                            connectionStates[address] = "Connecting"
                                        }
                                        Log.d(TAG, "Retry connection to $address")
                                    }
                                }
                            }, 1000)
                        } else {
                            Log.e(TAG, "Max retries reached for $address on error status=$status")
                            connectionRetries.remove(address)
                            isReconnectingForDistance.remove(address)
                            reconnectAttempts.remove(address)
                        }
                    }
                }
                BluetoothGatt.STATE_CONNECTING -> {
                    Log.d(TAG, "Connecting to $address")
                    if (!isReconnecting) {
                        connectionStates[address] = "Connecting"
                    }
                }
                BluetoothGatt.STATE_DISCONNECTING -> {
                    Log.d(TAG, "Disconnecting from $address")
                    if (!isReconnecting) {
                        connectionStates[address] = "Disconnecting"
                    }
                }
            }
            Log.d(TAG, "Current connectionStates: $connectionStates")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val address = gatt?.device?.address ?: return
            Log.d(TAG, "onServicesDiscovered for $address, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully for $address")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission missing for $address")
                    return
                }

                gatt.services?.forEach { service ->
                    Log.d(TAG, "Service UUID: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d(TAG, "  Characteristic UUID: ${char.uuid}, Properties: ${char.properties}")
                    }
                }

                val services = listOf(
                    Pair(BATTERY_SERVICE_UUID, BATTERY_LEVEL_UUID),
                    Pair(DISTANCE_SERVICE_UUID, DISTANCE_CHAR_UUID)
                )
                for ((serviceUuid, charUuid) in services) {
                    val service = gatt.getService(serviceUuid)
                    if (service != null) {
                        Log.d(TAG, "Service $serviceUuid found for $address")
                        val characteristic = service.getCharacteristic(charUuid)
                        if (characteristic != null) {
                            Log.d(TAG, "Characteristic $charUuid found, properties: ${characteristic.properties}")
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                enqueueGattOperation {
                                    if (gatt.setCharacteristicNotification(characteristic, true)) {
                                        Log.d(TAG, "setCharacteristicNotification enabled for $charUuid on $address")
                                    } else {
                                        Log.e(TAG, "Failed to enable setCharacteristicNotification for $charUuid on $address")
                                    }
                                    completeGattOperation()
                                }
                                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    writeDescriptorWithRetry(gatt, descriptor, characteristic, charUuid, address, 0)
                                } else {
                                    Log.e(TAG, "CCCD descriptor not found for $charUuid on $address")
                                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                        startPollingCharacteristic(gatt, characteristic, charUuid, address)
                                    }
                                }
                            } else {
                                Log.w(TAG, "Characteristic $charUuid does not support notifications on $address, properties=${characteristic.properties}")
                                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                    startPollingCharacteristic(gatt, characteristic, charUuid, address)
                                }
                            }
                        } else {
                            Log.e(TAG, "Characteristic $charUuid not found in service $serviceUuid on $address")
                        }
                    } else {
                        Log.e(TAG, "Service $serviceUuid not found on $address")
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed for $address, status=$status")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Retrying service discovery for $address")
                        enqueueGattOperation {
                            gatt.discoverServices()
                            completeGattOperation()
                        }
                    }, 1000)
                }
            }
        }

        private fun writeDescriptorWithRetry(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            characteristic: BluetoothGattCharacteristic,
            charUuid: UUID,
            address: String,
            attempt: Int
        ) {
            if (attempt >= 3) {
                Log.e(TAG, "Max retries reached for descriptor write for $charUuid on $address")
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    startPollingCharacteristic(gatt, characteristic, charUuid, address)
                }
                completeGattOperation()
                return
            }
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                enqueueGattOperation {
                    if (gatt.writeDescriptor(descriptor)) {
                        Log.d(TAG, "Attempt ${attempt + 1}: Descriptor write initiated for $charUuid on $address")
                    } else {
                        Log.e(TAG, "Attempt ${attempt + 1}: Failed to initiate descriptor write for $charUuid on $address")
                        Handler(Looper.getMainLooper()).postDelayed({
                            writeDescriptorWithRetry(gatt, descriptor, characteristic, charUuid, address, attempt + 1)
                        }, 500)
                        completeGattOperation()
                    }
                }
            } else {
                completeGattOperation()
            }
        }

        private fun startPollingCharacteristic(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            charUuid: UUID,
            address: String
        ) {
            Log.d(TAG, "Starting polling for $charUuid on $address")
            Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                override fun run() {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (connectionStates[address] == "Connected") {
                            enqueueGattOperation {
                                if (gatt.readCharacteristic(characteristic)) {
                                    Log.d(TAG, "Polling read initiated for $charUuid on $address")
                                } else {
                                    Log.e(TAG, "Failed to initiate polling read for $charUuid on $address")
                                }
                                completeGattOperation()
                            }
                            Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                        }
                    }
                }
            }, 1000)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            val address = gatt?.device?.address ?: return
            val uuid = descriptor?.uuid ?: return
            val characteristic = descriptor.characteristic
            val charUuid = characteristic?.uuid ?: "unknown"
            Log.d(TAG, "onDescriptorWrite for $uuid (char: $charUuid) on $address, status=$status")
            completeGattOperation()
            if (uuid == CLIENT_CHARACTERISTIC_CONFIG) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Successfully enabled notifications for characteristic $charUuid on $address")
                    subscriptionRetries.remove(address)
                } else {
                    Log.e(TAG, "Failed to enable notifications for $charUuid on $address, status=$status")
                    val retries = subscriptionRetries.getOrDefault(address, 0) + 1
                    if (retries < 3) {
                        subscriptionRetries[address] = retries
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val desc = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            if (desc != null) {
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                Handler(Looper.getMainLooper()).postDelayed({
                                    enqueueGattOperation {
                                        if (gatt.writeDescriptor(desc)) {
                                            Log.d(TAG, "Retry $retries: Descriptor write initiated for $charUuid on $address")
                                        } else {
                                            Log.e(TAG, "Retry $retries: Failed to initiate descriptor write for $charUuid on $address")
                                        }
                                        completeGattOperation()
                                    }
                                }, 500)
                            }
                        }
                    } else {
                        Log.e(TAG, "Max retries reached for enabling notifications for $charUuid on $address")
                        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            startPollingCharacteristic(gatt, characteristic, charUuid as UUID, address)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            completeGattOperation()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val address = gatt?.device?.address ?: return
                val uuid = characteristic?.uuid ?: return
                val value = characteristic.value?.let { bytes ->
                    when (uuid) {
                        BATTERY_LEVEL_UUID -> bytes[0].toInt().toString() + "%"
                        else -> String(bytes)
                    }
                } ?: "Unknown"
                val currentTime = System.currentTimeMillis()
                val requestTime = lastReadRequestTimes[address] ?: currentTime
                val latency = (currentTime - requestTime).toString() + " ms"

                val currentData = deviceData[address] ?: emptyMap()
                deviceData[address] = currentData + when (uuid) {
                    BATTERY_LEVEL_UUID -> "Battery" to value
                    else -> uuid.toString() to value
                } + ("Latency" to latency)
                Log.d(TAG, "Read $uuid for $address: $value, Latency: $latency")

                val batteryValue = if (uuid == BATTERY_LEVEL_UUID) value.removeSuffix("%").toIntOrNull() ?: 0 else 0
                val currentTagData = tagDataMap[address] ?: TagData(address, 0.0, 0.0, batteryValue)
                tagDataMap[address] = currentTagData.copy(battery = batteryValue)
            } else {
                Log.e(TAG, "Characteristic read failed for ${characteristic?.uuid} on ${gatt?.device?.address}, status=$status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            completeGattOperation()
            val address = gatt?.device?.address ?: return
            val uuid = characteristic?.uuid ?: return
            val bytes = characteristic.value ?: byteArrayOf()
            Log.d(TAG, "Notification for $uuid on $address: ${bytes.joinToString(" ") { "%02x".format(it) }}")

            val currentTagData = tagDataMap[address] ?: TagData(address, 0.0, 0.0, 0)
            when (uuid) {
                BATTERY_LEVEL_UUID -> {
                    if (bytes.isNotEmpty()) {
                        val batteryLevel = bytes[0].toInt()
                        tagDataMap[address] = currentTagData.copy(battery = batteryLevel)
                        deviceData[address] = (deviceData[address] ?: emptyMap()) + ("Battery" to "$batteryLevel%")
                        Log.d(TAG, "Battery notification for $address: $batteryLevel%")
                    } else {
                        Log.w(TAG, "Empty battery notification for $address")
                    }
                }
                DISTANCE_CHAR_UUID -> {
                    if (bytes.size >= 4) {
                        try {
                            val distanceMeters = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                            if (distanceMeters.isFinite()) {
                                val distanceCm = distanceMeters * 100.0
                                tagDataMap[address] = currentTagData.copy(distance = distanceCm.toDouble())
                                deviceData[address] = (deviceData[address] ?: emptyMap()) + ("Distance" to String.format("%.2f cm", distanceCm))
                                Log.d(TAG, "Distance notification for $address: $distanceCm cm")
                                if (distanceCm > 0) {
                                    isReconnectingForDistance.remove(address)
                                    reconnectAttempts.remove(address)
                                    Log.d(TAG, "Valid distance received for $address, stopping reconnection cycle")
                                }
                            } else {
                                Log.w(TAG, "Invalid distance value: $distanceMeters")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse distance for $address: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "Insufficient bytes for distance: ${bytes.size}")
                    }
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            completeGattOperation()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val address = gatt?.device?.address ?: return
                val currentTime = System.currentTimeMillis()
                val requestTime = lastReadRequestTimes[address] ?: currentTime
                val latency = (currentTime - requestTime).toString() + " ms"
                val currentData = deviceData[address] ?: emptyMap()
                deviceData[address] = currentData + ("RSSI" to "$rssi dBm") + ("Latency" to latency)
                Log.d(TAG, "RSSI for $address: $rssi dBm, Latency: $latency")
            } else {
                Log.e(TAG, "RSSI read failed for ${gatt?.device?.address}, status=$status")
            }
        }
    }

    // Class-level state variables to hold Compose state
    private lateinit var connectionStates: SnapshotStateMap<String, String>
    private lateinit var deviceData: SnapshotStateMap<String, Map<String, String>>
    private lateinit var tagDataMap: SnapshotStateMap<String, TagData>
    private lateinit var gattConnections: MutableMap<String, BluetoothGatt>
    private lateinit var connectionStartTimes: MutableMap<String, Long>
    private lateinit var lastReadRequestTimes: MutableMap<String, Long>
    private lateinit var savedDeviceAddress: MutableState<String?>

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btManager = getSystemService(BluetoothManager::class.java)
        permissionManager = PermissionManager(this)

        setContent {
            AtlasTheme {
                val navController = rememberNavController()
                val foundDevices = remember { mutableStateListOf<BleDevice>() }
                connectionStates = remember { mutableStateMapOf<String, String>() }
                gattConnections = remember { mutableMapOf<String, BluetoothGatt>() }
                deviceData = remember { mutableStateMapOf<String, Map<String, String>>() }
                tagDataMap = remember { mutableStateMapOf<String, TagData>() }
                connectionStartTimes = remember { mutableMapOf<String, Long>() }
                lastReadRequestTimes = remember { mutableMapOf<String, Long>() }
                val updateRate = remember { mutableStateOf(500L) }
                savedDeviceAddress = remember { mutableStateOf(getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE).getString("connectedDevice", null)) }
                val context = LocalContext.current

                val bleScanManager = remember {
                    BleScanManager(btManager, 5000, scanCallback = BleScanCallback({
                        val address = it?.device?.address ?: return@BleScanCallback
                        if (address.isBlank()) {
                            Log.w(TAG, "ScanResult has no valid address, skipping")
                            return@BleScanCallback
                        }
                        val hasScanPermission = ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasConnectPermission = ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                        val name: String? = if (hasScanPermission && hasConnectPermission) it.device?.name else null
                        val rssi: Int? = if (hasScanPermission && hasConnectPermission) it.rssi else null
                        val existingDeviceIndex = foundDevices.indexOfFirst { it.address == address }
                        if (existingDeviceIndex == -1) {
                            val device = BleDevice(address, name, rssi)
                            Log.d(TAG, "Adding new device: Address=$address, Name=$name, RSSI=$rssi")
                            foundDevices.add(device)
                        } else {
                            val existingDevice = foundDevices[existingDeviceIndex]
                            val updatedDevice = existingDevice.copy(name = name, rssi = rssi)
                            foundDevices[existingDeviceIndex] = updatedDevice
                            Log.d(TAG, "Updated device: Address=$address, Name=$name, RSSI=$rssi")
                        }
                    }))
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val grantResults = permissions.map { if (it.value) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }
                        .toIntArray()
                    permissionManager.dispatchOnRequestPermissionsResult(1, grantResults)
                }

                var isLoggedIn by remember { mutableStateOf(false) }
                var isConnected by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!isLoggedIn) {
                        navController.navigate("logIn") {
                            popUpTo(0)
                        }
                    } else if (!isConnected) {
                        navController.navigate("connection") {
                            popUpTo(0)
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    topBar = {
                        if (currentRoute != "logIn" && currentRoute != "register") {
                            TopNavBar(navController = navController)
                        }
                    },
                    bottomBar = {
                        if (currentRoute != "logIn" && currentRoute != "register") {
                            BottomNavBar(navController = navController)
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AppNavHost(
                            navController = navController,
                            permissionManager = permissionManager,
                            bleScanManager = bleScanManager,
                            foundDevices = foundDevices,
                            connectionStates = connectionStates,
                            savedDeviceAddress = savedDeviceAddress,
                            deviceData = deviceData,
                            connectionStartTimes = connectionStartTimes,
                            gattConnections = gattConnections,
                            context = context,
                            lastReadRequestTimes = lastReadRequestTimes,
                            updateRate = updateRate,
                            tagDataMap = tagDataMap,
                            onConnect = { address ->
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val device = btManager.adapter?.getRemoteDevice(address)
                                    if (device != null && connectionStates[address] != "Connected") {
                                        gattConnections[address]?.let { oldGatt ->
                                            Log.d(TAG, "Cleaning up old GATT for $address")
                                            oldGatt.disconnect()
                                            oldGatt.close()
                                            gattConnections.remove(address)
                                        }
                                        val gatt = device.connectGatt(this@MainActivity, false, gattCallback)
                                        gattConnections[address] = gatt
                                        connectionStates[address] = "Connecting"
                                        Log.d(TAG, "Initiating connection to $address, GATT created")
                                    } else {
                                        Log.w(TAG, "Device null or already connected: $address")
                                    }
                                }
                            },
                            onDisconnect = { address ->
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    gattConnections[address]?.let { gatt ->
                                        Log.d(TAG, "Disconnecting $address")
                                        gatt.disconnect()
                                        gatt.close()
                                        gattConnections.remove(address)
                                        connectionStates[address] = "Disconnected"
                                        isReconnectingForDistance.remove(address)
                                        reconnectAttempts.remove(address)
                                    } ?: run {
                                        Log.w(TAG, "No GATT found for $address, forcing state to Disconnected")
                                        connectionStates[address] = "Disconnected"
                                        isReconnectingForDistance.remove(address)
                                        reconnectAttempts.remove(address)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        @RequiresApi(Build.VERSION_CODES.S)
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }
}