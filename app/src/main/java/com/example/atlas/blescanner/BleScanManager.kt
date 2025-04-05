package com.example.atlas.blescanner

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.os.Looper

class BleScanManager(
    private val btManager: BluetoothManager,
    private val scanPeriod: Long,
    private val scanCallback: ScanCallback
) {
    private val bleScanner: BluetoothLeScanner? = btManager.adapter?.bluetoothLeScanner
    val beforeScanActions: MutableList<() -> Unit> = mutableListOf()
    val afterScanActions: MutableList<() -> Unit> = mutableListOf()

    fun scanBleDevices() {
        bleScanner?.let { scanner ->
            beforeScanActions.forEach { it.invoke() }
            scanner.startScan(scanCallback)
            Handler(Looper.getMainLooper()).postDelayed({
                scanner.stopScan(scanCallback)
                afterScanActions.forEach { it.invoke() }
            }, scanPeriod)
        }
    }
}