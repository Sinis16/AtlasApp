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
import com.example.atlas.models.Tracker
import com.example.atlas.navigation.AppNavHost
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.ui.components.BottomNavBar
import com.example.atlas.ui.components.TopNavBar
import com.example.atlas.ui.theme.AtlasTheme
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.atlas.data.repository.UserRepository
import com.example.atlas.ui.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var permissionManager: PermissionManager
    private lateinit var btManager: BluetoothManager
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val DISTANCE_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-567812345679")
    private val DEVICE_SERVICE_UUID = UUID.fromString("87654321-4321-6789-4321-678987654321")
    private val DEVICE_ID_CHAR_UUID = UUID.fromString("87654321-4321-6789-4321-678987654322")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val subscriptionRetries = mutableMapOf<String, Int>()
    private val connectionRetries = mutableMapOf<String, Int>()
    private val gattOperationQueue = mutableListOf<() -> Unit>()
    private var isProcessingGattOperation = false
    private val isReconnectingForDistance = mutableMapOf<String, Boolean>()
    private val reconnectAttempts = mutableMapOf<String, Int>()
    private val deviceIdToAddress = mutableMapOf<String, String>()
    private val rxToTxId = mutableMapOf<String, String>()

    private fun enqueueGattOperation(operation: () -> Unit) {
        Log.d(TAG, "Enqueuing GATT operation, queue size: ${gattOperationQueue.size}")
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
        Log.d(TAG, "Checking distance for $address: distanceStr=$distanceStr, distanceCm=$distanceCm")
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

    private fun getDynamicDistanceServiceUUID(txId: String): UUID {
        return UUID.fromString("12345678-1234-5678-1234-${txId}12345678")
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
                        rxToTxId.remove(address)
                        if (savedDeviceAddress.value == address) {
                            getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                                .edit().remove("connectedDevice").apply()
                            savedDeviceAddress.value = null
                        }
                    }
                    if (status == 133 || status == 147) {
                        val retries = connectionRetries.getOrDefault(address, 0) + 1
                        if (retries < 5) {
                            Log.d(TAG, "Connection error (status=$status) for $address, retrying ($retries/5)")
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

                val expectedPrefix = "12345678-1234-5678-1234-"
                val expectedSuffix = "12345678"

                gatt.services?.forEach { service ->
                    val serviceUuid = service.uuid.toString()
                    Log.d(TAG, "Service found: $serviceUuid")
                    service.characteristics.forEach { char ->
                        Log.d(TAG, "  Characteristic: ${char.uuid}, Properties: ${char.properties}")
                    }

                    if (service.uuid == BATTERY_SERVICE_UUID) {
                        val characteristic = service.getCharacteristic(BATTERY_LEVEL_UUID)
                        if (characteristic != null) {
                            Log.d(TAG, "Battery characteristic found for $address")
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                enqueueGattOperation {
                                    if (gatt.setCharacteristicNotification(characteristic, true)) {
                                        Log.d(TAG, "setCharacteristicNotification enabled for $BATTERY_LEVEL_UUID on $address")
                                    } else {
                                        Log.e(TAG, "Failed to enable setCharacteristicNotification for $BATTERY_LEVEL_UUID on $address")
                                    }
                                    completeGattOperation()
                                }
                                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    writeDescriptorWithRetry(gatt, descriptor, characteristic, BATTERY_LEVEL_UUID, address, 0)
                                } else {
                                    Log.e(TAG, "CCCD descriptor not found for $BATTERY_LEVEL_UUID on $address")
                                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                        startPollingCharacteristic(gatt, characteristic, BATTERY_LEVEL_UUID, address)
                                    }
                                }
                            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                startPollingCharacteristic(gatt, characteristic, BATTERY_LEVEL_UUID, address)
                            }
                        } else {
                            Log.w(TAG, "Battery characteristic $BATTERY_LEVEL_UUID not found in service $BATTERY_SERVICE_UUID on $address")
                        }
                    }

                    if (service.uuid == DEVICE_SERVICE_UUID) {
                        val characteristic = service.getCharacteristic(DEVICE_ID_CHAR_UUID)
                        if (characteristic != null) {
                            Log.d(TAG, "Device ID characteristic found for $address")
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                enqueueGattOperation {
                                    if (gatt.readCharacteristic(characteristic)) {
                                        Log.d(TAG, "Reading Device ID for $address")
                                    } else {
                                        Log.e(TAG, "Failed to read Device ID for $address")
                                    }
                                    completeGattOperation()
                                }
                            }
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                enqueueGattOperation {
                                    if (gatt.setCharacteristicNotification(characteristic, true)) {
                                        Log.d(TAG, "setCharacteristicNotification enabled for $DEVICE_ID_CHAR_UUID on $address")
                                    } else {
                                        Log.e(TAG, "Failed to enable setCharacteristicNotification for $DEVICE_ID_CHAR_UUID on $address")
                                    }
                                    completeGattOperation()
                                }
                                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    writeDescriptorWithRetry(gatt, descriptor, characteristic, DEVICE_ID_CHAR_UUID, address, 0)
                                }
                            }
                        } else {
                            Log.w(TAG, "Device ID characteristic $DEVICE_ID_CHAR_UUID not found in service $DEVICE_SERVICE_UUID on $address")
                        }
                    }

                    val serviceUuidStr = service.uuid.toString().lowercase()
                    if (serviceUuidStr.startsWith(expectedPrefix) && serviceUuidStr.endsWith(expectedSuffix)) {
                        val txId = serviceUuidStr.substring(24, 28).uppercase()
                        Log.d(TAG, "Dynamic Distance Service found: $serviceUuidStr, TX ID $txId on $address")
                        rxToTxId[address] = txId
                        val currentData = deviceData[address] ?: emptyMap<String, String>()
                        deviceData[address] = currentData + mapOf("ReceivedTxId" to txId)
                        Log.d(TAG, "Updated deviceData for $address: ReceivedTxId=$txId")
                        val characteristic = service.getCharacteristic(DISTANCE_CHAR_UUID)
                        if (characteristic != null) {
                            Log.d(TAG, "Distance characteristic $DISTANCE_CHAR_UUID found for TX ID $txId on $address, properties=${characteristic.properties}")
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                enqueueGattOperation {
                                    if (gatt.setCharacteristicNotification(characteristic, true)) {
                                        Log.d(TAG, "setCharacteristicNotification enabled for $DISTANCE_CHAR_UUID (TX ID $txId) on $address")
                                    } else {
                                        Log.e(TAG, "Failed to enable setCharacteristicNotification for $DISTANCE_CHAR_UUID (TX ID $txId) on $address")
                                        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                            startPollingCharacteristic(gatt, characteristic, DISTANCE_CHAR_UUID, address, txId)
                                        }
                                    }
                                    completeGattOperation()
                                }
                                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    Log.d(TAG, "Attempting CCCD descriptor write for $DISTANCE_CHAR_UUID (TX ID $txId) on $address")
                                    writeDescriptorWithRetry(gatt, descriptor, characteristic, DISTANCE_CHAR_UUID, address, 0, txId)
                                } else {
                                    Log.e(TAG, "CCCD descriptor not found for $DISTANCE_CHAR_UUID (TX ID $txId) on $address")
                                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                        startPollingCharacteristic(gatt, characteristic, DISTANCE_CHAR_UUID, address, txId)
                                    }
                                }
                            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                Log.d(TAG, "Distance characteristic supports READ, starting polling for $address")
                                startPollingCharacteristic(gatt, characteristic, DISTANCE_CHAR_UUID, address, txId)
                            } else {
                                Log.e(TAG, "Distance characteristic $DISTANCE_CHAR_UUID does not support NOTIFY or READ on $address")
                            }
                        } else {
                            Log.e(TAG, "Distance characteristic $DISTANCE_CHAR_UUID not found in service $serviceUuidStr on $address")
                        }
                    }
                }
                if (gatt.services?.none { it.uuid.toString().lowercase().startsWith(expectedPrefix) && it.uuid.toString().lowercase().endsWith(expectedSuffix) } == true) {
                    Log.w(TAG, "No dynamic distance service found for $address")
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
            attempt: Int,
            txId: String? = null
        ) {
            if (attempt >= 3) {
                Log.e(TAG, "Max retries reached for descriptor write for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    startPollingCharacteristic(gatt, characteristic, charUuid, address, txId)
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
                        Log.d(TAG, "Attempt ${attempt + 1}: Descriptor write initiated for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
                    } else {
                        Log.e(TAG, "Attempt ${attempt + 1}: Failed to initiate descriptor write for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
                        Handler(Looper.getMainLooper()).postDelayed({
                            writeDescriptorWithRetry(gatt, descriptor, characteristic, charUuid, address, attempt + 1, txId)
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
            address: String,
            txId: String? = null
        ) {
            Log.d(TAG, "Starting polling for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
            Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                override fun run() {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (connectionStates[address] == "Connected") {
                            enqueueGattOperation {
                                lastReadRequestTimes[address] = System.currentTimeMillis()
                                if (gatt.readCharacteristic(characteristic)) {
                                    Log.d(TAG, "Polling read initiated for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
                                } else {
                                    Log.e(TAG, "Failed to initiate polling read for $charUuid on $address${txId?.let { " (TX ID $it)" } ?: ""}")
                                }
                                completeGattOperation()
                            }
                            Handler(Looper.getMainLooper()).postDelayed(this, 1500)
                        }
                    }
                }
            }, 1500)
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
                        DEVICE_ID_CHAR_UUID -> String(bytes)
                        DISTANCE_CHAR_UUID -> {
                            if (bytes.size >= 4) {
                                try {
                                    val distanceMeters = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                                    if (distanceMeters.isFinite()) {
                                        val distanceCm = distanceMeters * 100.0
                                        String.format("%.2f cm Karabiner", distanceCm)
                                    } else {
                                        "Invalid"
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse distance read: ${e.message}")
                                    "Error"
                                }
                            } else {
                                "Insufficient bytes"
                            }
                        }
                        else -> String(bytes)
                    }
                } ?: "Unknown"
                val currentTime = System.currentTimeMillis()
                val requestTime = lastReadRequestTimes[address] ?: currentTime
                val latency = (currentTime - requestTime).toString() + " ms"

                val currentData = deviceData[address] ?: emptyMap<String, String>()
                val newData: Map<String, String> = when (uuid) {
                    BATTERY_LEVEL_UUID -> mapOf("Battery" to value)
                    DEVICE_ID_CHAR_UUID -> {
                        val txId = value.removePrefix("UWB_TX_").uppercase()
                        if (txId.length == 4) {
                            deviceIdToAddress[txId] = address
                            Log.d(TAG, "Mapped TX ID $txId to TX address $address")
                        } else {
                            Log.w(TAG, "Invalid Device ID format: $value")
                        }
                        mapOf("DeviceID" to value)
                    }
                    DISTANCE_CHAR_UUID -> {
                        if (value.endsWith("cm")) {
                            val txId = rxToTxId[address] ?: "Unknown"
                            val txAddress = deviceIdToAddress[txId]
                            if (txAddress != null) {
                                val txCurrentData = deviceData[txAddress] ?: emptyMap<String, String>()
                                deviceData[txAddress] = txCurrentData + mapOf("Distance" to value)
                                val distanceCm = value.replace(" cm", "").toDoubleOrNull() ?: 0.0
                                val txTagData = tagDataMap[txAddress] ?: TagData(txAddress, 0.0, 0.0, 0)
                                tagDataMap[txAddress] = txTagData.copy(distance = distanceCm)
                                Log.d(TAG, "Read distance $value for TX $txAddress (from RX $address, TX ID $txId)")
                                mapOf()
                            } else {
                                Log.w(TAG, "No TX address for TX ID $txId, storing distance $value under RX $address")
                                mapOf("Distance" to value)
                            }
                        } else {
                            Log.w(TAG, "Invalid distance read: $value")
                            mapOf("Distance" to value)
                        }
                    }
                    else -> mapOf(uuid.toString() to value)
                }
                deviceData[address] = currentData + newData + mapOf("Latency" to latency)
                Log.d(TAG, "Read $uuid for $address: $value, Latency: $latency")

                val batteryValue = if (uuid == BATTERY_LEVEL_UUID) value.removeSuffix("%").toIntOrNull() ?: 0 else 0
                val distanceValue = if (uuid == DISTANCE_CHAR_UUID && value.endsWith("cm")) value.replace(" cm", "").toDoubleOrNull() ?: 0.0 else 0.0
                val currentTagData = tagDataMap[address] ?: TagData(address, 0.0, 0.0, batteryValue)
                tagDataMap[address] = currentTagData.copy(battery = batteryValue, distance = distanceValue)
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
                        deviceData[address] = (deviceData[address] ?: emptyMap()) + mapOf("Battery" to "$batteryLevel%")
                        Log.d(TAG, "Battery notification for $address: $batteryLevel%")
                    } else {
                        Log.w(TAG, "Empty battery notification for $address")
                    }
                }
                DEVICE_ID_CHAR_UUID -> {
                    val deviceId = String(bytes)
                    val txId = deviceId.removePrefix("UWB_TX_").uppercase()
                    if (txId.length == 4) {
                        deviceIdToAddress[txId] = address
                        Log.d(TAG, "Device ID notification for $address: $deviceId, mapped TX ID $txId to $address")
                    } else {
                        Log.w(TAG, "Invalid Device ID format: $deviceId")
                    }
                    deviceData[address] = (deviceData[address] ?: emptyMap()) + mapOf("DeviceID" to deviceId)
                }
                DISTANCE_CHAR_UUID -> {
                    if (bytes.size >= 4) {
                        try {
                            val distanceMeters = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                            if (distanceMeters.isFinite()) {
                                val distanceCm = distanceMeters * 100.0
                                val serviceUuid = gatt.services.find { svc ->
                                    svc.getCharacteristic(DISTANCE_CHAR_UUID) == characteristic
                                }?.uuid?.toString() ?: ""
                                val txId = if (serviceUuid.lowercase().startsWith("12345678-1234-5678-1234-") && serviceUuid.lowercase().endsWith("12345678")) {
                                    serviceUuid.substring(24, 28).uppercase()
                                } else {
                                    rxToTxId[address]
                                }
                                Log.d(TAG, "Distance notification on $address, service UUID $serviceUuid, TX ID $txId")
                                if (txId != null) {
                                    val txAddress = deviceIdToAddress[txId]
                                    if (txAddress != null) {
                                        val txTagData = tagDataMap[txAddress] ?: TagData(txAddress, 0.0, 0.0, 0)
                                        tagDataMap[txAddress] = txTagData.copy(distance = distanceCm.toDouble())
                                        deviceData[txAddress] = (deviceData[txAddress] ?: emptyMap()) + mapOf("Distance" to String.format("%.2f cm", distanceCm))
                                        Log.d(TAG, "Assigned distance $distanceCm cm to TX $txAddress (from RX $address, TX ID $txId)")
                                        if (distanceCm > 0) {
                                            isReconnectingForDistance.remove(address)
                                            reconnectAttempts.remove(address)
                                            Log.d(TAG, "Valid distance received for TX $txAddress, stopping reconnection cycle")
                                        }
                                    } else {
                                        Log.w(TAG, "No TX address mapped for TX ID $txId, storing under RX $address temporarily")
                                        tagDataMap[address] = currentTagData.copy(distance = distanceCm.toDouble())
                                        deviceData[address] = (deviceData[address] ?: emptyMap()) + mapOf("Distance" to String.format("%.2f cm", distanceCm))
                                    }
                                } else {
                                    Log.e(TAG, "No TX ID found for distance notification on $address")
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
                val currentData = deviceData[address] ?: emptyMap<String, String>()
                deviceData[address] = currentData + mapOf("RSSI" to "$rssi dBm", "Latency" to latency)
                Log.d(TAG, "RSSI for $address: $rssi dBm, Latency: $latency")
            } else {
                Log.e(TAG, "RSSI read failed for ${gatt?.device?.address}, status=$status")
            }
        }
    }

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
                val updateRate = remember { mutableStateOf(1000L) }
                savedDeviceAddress = remember { mutableStateOf(getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE).getString("connectedDevice", null)) }

                val userViewModel: UserViewModel = hiltViewModel()

                val leaveBehindDistance = remember {
                    mutableStateOf(
                        getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                            .getLong("leaveBehindDistance", 400L)
                    )
                }
                val isLeaveBehindEnabled = remember {
                    mutableStateOf(
                        getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                            .getBoolean("isLeaveBehindEnabled", true)
                    )
                }
                val isAdvancedMode = remember {
                    mutableStateOf(
                        getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                            .getBoolean("isAdvancedMode", false)
                    )
                }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                LaunchedEffect(leaveBehindDistance.value, isLeaveBehindEnabled.value, isAdvancedMode.value) {
                    getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("leaveBehindDistance", leaveBehindDistance.value)
                        .putBoolean("isLeaveBehindEnabled", isLeaveBehindEnabled.value)
                        .putBoolean("isAdvancedMode", isAdvancedMode.value)
                        .apply()
                }

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
                        val name: String? = if (hasScanPermission && hasConnectPermission) it?.device?.name else null
                        val rssi: Int? = if (hasScanPermission && hasConnectPermission) it?.rssi else null
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
                            leaveBehindDistance = leaveBehindDistance,
                            isLeaveBehindEnabled = isLeaveBehindEnabled,
                            isAdvancedMode = isAdvancedMode,
                            userRepository = userRepository,
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

                                        // Add tracker to Supabase if not already present
                                        scope.launch {
                                            try {
                                                val deviceName = foundDevices.find { it.address == address }?.name ?: "Unknown"
                                                if (deviceName.startsWith("Proton") || deviceName.startsWith("Electron")) {
                                                    val user = userRepository.getCurrentUser()
                                                    if (user?.id == null) {
                                                        Log.w(TAG, "No user logged in, skipping tracker creation for $address")
                                                        return@launch
                                                    }
                                                    // Check if tracker exists
                                                    val existingTrackers = supabaseClient.from("trackers")
                                                        .select { filter { eq("ble_id", address) } }
                                                        .decodeList<Tracker>()
                                                    if (existingTrackers.isEmpty()) {
                                                        // Create new tracker
                                                        val tracker = Tracker(
                                                            ble_id = address,
                                                            name = deviceName,
                                                            user1 = user.id,
                                                            user2 = null,
                                                            user3 = null,
                                                            last_connection = null,
                                                            last_latitude = null,
                                                            last_longitude = null,
                                                            type = if (deviceName.startsWith("Proton")) "Proton" else "Electron"
                                                        )
                                                        supabaseClient.from("trackers").insert(tracker)
                                                        Log.d(TAG, "Added new tracker for $address: $tracker")
                                                    } else {
                                                        Log.d(TAG, "Tracker already exists for $address")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to add tracker for $address: ${e.message}")
                                            }
                                        }
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
                                        rxToTxId.remove(address)
                                    } ?: run {
                                        Log.w(TAG, "No GATT found for $address, forcing state to Disconnected")
                                        connectionStates[address] = "Disconnected"
                                        isReconnectingForDistance.remove(address)
                                        reconnectAttempts.remove(address)
                                        rxToTxId.remove(address)
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