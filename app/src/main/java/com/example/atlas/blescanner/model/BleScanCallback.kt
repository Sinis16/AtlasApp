package com.example.atlas.blescanner.model

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

class BleScanCallback(
    private val callback: (ScanResult?) -> Unit
) : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        callback.invoke(result)
    }
}