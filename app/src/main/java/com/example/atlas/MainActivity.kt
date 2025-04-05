package com.example.atlas

import android.Manifest
import android.bluetooth.BluetoothManager
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.rememberNavController
import com.example.atlas.blescanner.BleScanManager
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.blescanner.model.BleScanCallback
import com.example.atlas.navigation.AppNavHost
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    private lateinit var btManager: BluetoothManager

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize non-Compose components
        btManager = getSystemService(BluetoothManager::class.java)
        permissionManager = PermissionManager(this)

        setContent {
            AtlasTheme {
                val navController = rememberNavController()

                // Create a reactive list for found devices
                val foundDevices = remember { mutableStateListOf<BleDevice>() }

                // Initialize BleScanManager
                val bleScanManager = remember {
                    BleScanManager(btManager, 5000, scanCallback = BleScanCallback({
                        val address = it?.device?.address
                        if (address.isNullOrBlank()) {
                            Log.w(TAG, "ScanResult has no valid address, skipping")
                            return@BleScanCallback
                        }

                        val hasScanPermission = ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasConnectPermission = ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

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

                // Permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val grantResults = permissions.map { if (it.value) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }
                        .toIntArray()
                    permissionManager.dispatchOnRequestPermissionsResult(1, grantResults)
                }

                // Mocked states for now, replace with actual logic
                var isLoggedIn by remember { mutableStateOf(false) }
                var isConnected by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!isLoggedIn) {
                        navController.navigate("logIn") {
                            popUpTo(0)  // Clear stack
                        }
                    } else if (!isConnected) {
                        navController.navigate("connection") {
                            popUpTo(0)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = navController,
                        permissionManager = permissionManager,
                        bleScanManager = bleScanManager,
                        foundDevices = foundDevices
                    )
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