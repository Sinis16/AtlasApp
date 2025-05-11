package com.example.atlas.ui.screens

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.models.TagData
import kotlin.math.round

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

    // Parse distance from deviceData or tagData
    val distanceCm = data["Distance"]?.let { distanceStr ->
        distanceStr.replace(" cm", "").toDoubleOrNull()
    } ?: tagData?.distance ?: 0.0

    // Round distance to nearest 10 cm
    val roundedDistance = (round(distanceCm / 10) * 10).toInt()

    // Calculate color based on distance (0 cm = bright red, 1000 cm = dark blue)
    val color = when {
        distanceCm <= 0 -> Color(0xFFFF0000) // Bright red
        distanceCm >= 1000 -> Color(0xFF00008B) // Dark blue
        else -> {
            val ratio = distanceCm / 1000.0
            val r = (255 * (1 - ratio)).toInt()
            val b = (139 * ratio).toInt()
            Color(r, 0, b)
        }
    }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(1f) // Make the Box square
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$roundedDistance cm",
                    color = Color.White,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            tagData?.let {
                Log.d(TAG, "Tag data for $tagId: id=${it.id}, distance=${it.distance}, battery=${it.battery}")
            } ?: run {
                Log.w(TAG, "No tagData for $tagId")
            }
        }

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