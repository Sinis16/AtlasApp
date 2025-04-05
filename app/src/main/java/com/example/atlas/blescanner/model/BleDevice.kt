package com.example.atlas.blescanner.model

data class BleDevice(
    val address: String,
    val name: String? = null,
    val rssi: Int? = null
) {
    companion object {
        fun createBleDevicesList(): MutableList<BleDevice> = mutableListOf()
    }
}