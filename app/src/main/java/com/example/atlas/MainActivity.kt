package com.example.atlas

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
                val connectionStartTimes = remember { mutableMapOf<String, Long>() }
                val lastReadRequestTimes = remember { mutableMapOf<String, Long>() }
                val updateRate = remember { mutableStateOf(500L) } // Default to Normal (500ms)
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
                                    if (ActivityCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        gatt.discoverServices()
                                    }
                                }
                                BluetoothGatt.STATE_DISCONNECTED -> {
                                    Log.d(TAG, "Disconnected from $address")
                                    connectionStates[address] = "Disconnected"
                                    gattConnections.remove(address)?.close()
                                    deviceData.remove(address)
                                    connectionStartTimes.remove(address)
                                    lastReadRequestTimes.remove(address)
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
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val address = gatt?.device?.address ?: return
                                Log.d(TAG, "Services discovered for $address")
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) return

                                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                                batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)?.let { char ->
                                    gatt.readCharacteristic(char)
                                }

                                val deviceInfoService = gatt.getService(DEVICE_INFO_SERVICE_UUID)
                                deviceInfoService?.getCharacteristic(FIRMWARE_VERSION_UUID)?.let { char ->
                                    gatt.readCharacteristic(char)
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
                            updateRate = updateRate, // Pass updateRate
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