package com.example.atlas.ui.screens

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.models.TagData

private const val TAG = "TagScreen"

@Composable
fun TagScreen(
    navController: NavHostController,
    connectionStates: SnapshotStateMap<String, String>,
    foundDevices: MutableList<BleDevice>,
    deviceData: SnapshotStateMap<String, Map<String, String>>,
    connectionStartTimes: MutableMap<String, Long>,
    gattConnections: MutableMap<String, BluetoothGatt>,
    context: Context,
    lastReadRequestTimes: MutableMap<String, Long>,
    updateRate: MutableState<Long>,
    tagDataMap: SnapshotStateMap<String, TagData>,
    tagId: String
) {
    val device = foundDevices.find { it.address == tagId }
    val name = device?.name ?: "Unknown Device"
    val data = deviceData[tagId] ?: emptyMap()
    val tagData = tagDataMap[tagId]
    val connectionState = connectionStates[tagId] ?: "Disconnected"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tag Details for $name",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (connectionState != "Connected") {
            Text(
                text = "Tag is not connected",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "Tag ID: $tagId",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "Distance: ${data["Distance"] ?: "N/A"}",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "Battery: ${data["Battery"] ?: "N/A"}",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "RSSI: ${data["RSSI"] ?: "N/A"}",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "Latency: ${data["Latency"] ?: "N/A"}",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            tagData?.let {
                Log.d(TAG, "Tag data for $tagId: id=${it.id}, distance=${it.distance}, battery=${it.battery}")
            } ?: run {
                Log.w(TAG, "No tagData for $tagId")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                navController.popBackStack()
                Log.d(TAG, "Navigating back from TagScreen for tagId: $tagId")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Back")
        }
    }
}