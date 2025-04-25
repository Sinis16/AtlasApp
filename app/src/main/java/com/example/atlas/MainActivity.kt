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
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    private lateinit var btManager: BluetoothManager

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    private val FIRMWARE_VERSION_UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
    private val DISTANCE_SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
    private val DISTANCE_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-567812345679")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val subscriptionRetries = mutableMapOf<String, Int>()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        btManager = getSystemService(BluetoothManager::class.java)
        permissionManager = PermissionManager(this)

        setContent {
            AtlasTheme {
                val navController = rememberNavController()
                val foundDevices = remember { mutableStateListOf<BleDevice>() }
                val connectionStates = remember { mutableStateMapOf<String, String>() }
                val gattConnections = remember { mutableMapOf<String, BluetoothGatt>() }
                val deviceData = remember { mutableStateMapOf<String, Map<String, String>>() }
                val tagDataMap = remember { mutableStateMapOf<String, TagData>() }
                val connectionStartTimes = remember { mutableMapOf<String, Long>() }
                val lastReadRequestTimes = remember { mutableMapOf<String, Long>() }
                val updateRate = remember { mutableStateOf(500L) }
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

                        val name: String?
                        val rssi: Int?
                        if (hasScanPermission && hasConnectPermission) {
                            name = it.device?.name
                            rssi = it.rssi
                        } else {
                            name = null
                            rssi = null
                            Log.w(TAG, "Missing permissions (SCAN: $hasScanPermission, CONNECT: $hasConnectPermission), using address only for $address")
                        }

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

                val prefs = getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                val savedDeviceAddress = remember { mutableStateOf(prefs.getString("connectedDevice", null)) }

                val gattCallback = remember {
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                            val address = gatt?.device?.address ?: return
                            Log.d(TAG, "Gatt callback for $address: status=$status, newState=$newState")
                            when (newState) {
                                BluetoothGatt.STATE_CONNECTED -> {
                                    Log.d(TAG, "Connected to $address")
                                    connectionStates[address] = "Connected"
                                    prefs.edit().putString("connectedDevice", address).apply()
                                    savedDeviceAddress.value = address
                                    connectionStartTimes[address] = System.currentTimeMillis()
                                    subscriptionRetries[address] = 0
                                    if (ActivityCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        gatt.discoverServices()
                                    } else {
                                        Log.w(TAG, "BLUETOOTH_CONNECT permission missing for $address")
                                    }
                                }
                                BluetoothGatt.STATE_DISCONNECTED -> {
                                    Log.d(TAG, "Disconnected from $address")
                                    connectionStates[address] = "Disconnected"
                                    gattConnections.remove(address)?.close()
                                    deviceData.remove(address)
                                    tagDataMap.remove(address)
                                    connectionStartTimes.remove(address)
                                    lastReadRequestTimes.remove(address)
                                    subscriptionRetries.remove(address)
                                    if (savedDeviceAddress.value == address) {
                                        prefs.edit().remove("connectedDevice").apply()
                                        savedDeviceAddress.value = null
                                    }
                                }
                                BluetoothGatt.STATE_CONNECTING -> {
                                    Log.d(TAG, "Connecting to $address")
                                    connectionStates[address] = "Connecting"
                                }
                                BluetoothGatt.STATE_DISCONNECTING -> {
                                    Log.d(TAG, "Disconnecting from $address")
                                    connectionStates[address] = "Disconnecting"
                                }
                            }
                            Log.d(TAG, "Current connectionStates: $connectionStates")
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                            val address = gatt?.device?.address ?: return
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "Services discovered for $address")
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    Log.w(TAG, "BLUETOOTH_CONNECT permission missing for $address")
                                    return
                                }

                                // Log all services and characteristics
                                gatt.services?.forEach { service ->
                                    Log.d(TAG, "Service UUID: ${service.uuid}")
                                    service.characteristics.forEach { char ->
                                        Log.d(TAG, "  Characteristic UUID: ${char.uuid}, Properties: ${char.properties}")
                                    }
                                }

                                // Battery service
                                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                                if (batteryService != null) {
                                    batteryService.getCharacteristic(BATTERY_LEVEL_UUID)?.let { char ->
                                        gatt.readCharacteristic(char)
                                        Log.d(TAG, "Reading battery characteristic for $address")
                                    } ?: Log.w(TAG, "Battery characteristic not found for $address")
                                } else {
                                    Log.w(TAG, "Battery service not found for $address")
                                }

                                // Device info service
                                val deviceInfoService = gatt.getService(DEVICE_INFO_SERVICE_UUID)
                                if (deviceInfoService != null) {
                                    deviceInfoService.getCharacteristic(FIRMWARE_VERSION_UUID)?.let { char ->
                                        gatt.readCharacteristic(char)
                                        Log.d(TAG, "Reading firmware characteristic for $address")
                                    } ?: Log.w(TAG, "Firmware characteristic not found for $address")
                                } else {
                                    Log.w(TAG, "Device info service not found for $address")
                                }

                                // Custom distance service
                                val distanceService = gatt.getService(DISTANCE_SERVICE_UUID)
                                if (distanceService != null) {
                                    Log.d(TAG, "Distance service found for $address")
                                    distanceService.getCharacteristic(DISTANCE_CHAR_UUID)?.let { char ->
                                        if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                            gatt.setCharacteristicNotification(char, true)
                                            val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                            if (descriptor != null) {
                                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    gatt.writeDescriptor(descriptor)
                                                    Log.d(TAG, "Attempting to enable notifications for distance characteristic on $address")
                                                }, 100)
                                            } else {
                                                Log.e(TAG, "CCCD descriptor not found for distance characteristic on $address")
                                            }
                                        } else {
                                            Log.e(TAG, "Distance characteristic does not support notifications on $address")
                                        }
                                    } ?: Log.e(TAG, "Distance characteristic not found for $address")
                                } else {
                                    Log.e(TAG, "Distance service not found for $address")
                                    // Try refreshing GATT cache
                                    try {
                                        val refreshMethod = gatt.javaClass.getMethod("refresh")
                                        if (refreshMethod.invoke(gatt) as Boolean) {
                                            Log.d(TAG, "GATT cache refreshed for $address, retrying discovery")
                                            gatt.discoverServices()
                                        } else {
                                            Log.e(TAG, "GATT cache refresh failed for $address")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "GATT cache refresh not supported: ${e.message}")
                                    }
                                }
                            } else {
                                Log.e(TAG, "Service discovery failed for $address, status=$status")
                            }
                        }

                        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                            val address = gatt?.device?.address ?: return
                            val uuid = descriptor?.uuid ?: return
                            if (uuid == CLIENT_CHARACTERISTIC_CONFIG) {
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(TAG, "Successfully enabled notifications for distance characteristic on $address")
                                    subscriptionRetries.remove(address)
                                } else {
                                    Log.e(TAG, "Failed to enable notifications for $address, status=$status")
                                    val retries = subscriptionRetries.getOrDefault(address, 0) + 1
                                    if (retries < 3) {
                                        subscriptionRetries[address] = retries
                                        if (ActivityCompat.checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission.BLUETOOTH_CONNECT
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            gatt.getService(DISTANCE_SERVICE_UUID)?.getCharacteristic(DISTANCE_CHAR_UUID)?.let { char ->
                                                gatt.setCharacteristicNotification(char, true)
                                                val desc = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                                desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    gatt.writeDescriptor(desc)
                                                    Log.d(TAG, "Retrying notification enable for $address, attempt $retries")
                                                }, 100)
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "Max retries reached for enabling notifications on $address")
                                    }
                                }
                            }
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic?,
                            status: Int
                        ) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val address = gatt?.device?.address ?: return
                                val uuid = characteristic?.uuid ?: return
                                val value = characteristic.value?.let { bytes ->
                                    if (uuid == BATTERY_LEVEL_UUID) {
                                        bytes[0].toInt().toString() + "%"
                                    } else {
                                        String(bytes)
                                    }
                                } ?: "Unknown"
                                val currentTime = System.currentTimeMillis()
                                val requestTime = lastReadRequestTimes[address] ?: currentTime
                                val latency = (currentTime - requestTime).toString() + " ms"

                                val currentData = deviceData[address] ?: emptyMap()
                                deviceData[address] = currentData + when (uuid) {
                                    BATTERY_LEVEL_UUID -> "Battery" to value
                                    FIRMWARE_VERSION_UUID -> "Firmware" to value
                                    else -> uuid.toString() to value
                                } + ("Latency" to latency)
                                Log.d(TAG, "Read $uuid for $address: $value, Latency: $latency")

                                // Update TagData with battery
                                val batteryValue = value.removeSuffix("%").toIntOrNull() ?: 0
                                val currentTagData = tagDataMap[address] ?: TagData(address, 0.0, 0.0, batteryValue)
                                tagDataMap[address] = currentTagData.copy(battery = batteryValue)
                            }
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic?
                        ) {
                            val address = gatt?.device?.address ?: return
                            val uuid = characteristic?.uuid ?: return
                            if (uuid == DISTANCE_CHAR_UUID) {
                                val distanceMeters = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                                val distanceCm = distanceMeters * 100.0
                                val currentTagData = tagDataMap[address] ?: TagData(address, 0.0, 0.0, 0)
                                tagDataMap[address] = currentTagData.copy(distance = distanceCm)
                                Log.d(TAG, "Distance notification for $address: $distanceCm cm")
                            }
                        }

                        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val address = gatt?.device?.address ?: return
                                val currentTime = System.currentTimeMillis()
                                val requestTime = lastReadRequestTimes[address] ?: currentTime
                                val latency = (currentTime - requestTime).toString() + " ms"
                                val currentData = deviceData[address] ?: emptyMap()
                                deviceData[address] = currentData + ("RSSI" to "$rssi dBm") + ("Latency" to latency)
                                Log.d(TAG, "RSSI for $address: $rssi dBm, Latency: $latency")
                            }
                        }
                    }
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
                                    } ?: run {
                                        Log.w(TAG, "No GATT found for $address, forcing state to Disconnected")
                                        connectionStates[address] = "Disconnected"
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