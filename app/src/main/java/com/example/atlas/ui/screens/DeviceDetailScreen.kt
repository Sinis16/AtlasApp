package com.example.atlas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.atlas.models.TagData
import com.example.atlas.ui.viewmodel.TrackerViewModel
import androidx.compose.runtime.snapshots.SnapshotStateMap

@Composable
fun DeviceDetailScreen(
    navController: NavHostController,
    bleId: String,
    connectionStates: SnapshotStateMap<String, String>,
    deviceData: SnapshotStateMap<String, Map<String, String>>,
    tagDataMap: SnapshotStateMap<String, TagData>,
    modifier: Modifier = Modifier
) {
    val trackerViewModel: TrackerViewModel = hiltViewModel()
    val tracker by trackerViewModel.selectedTracker.collectAsState()
    val connectionStatus = connectionStates[bleId] ?: "Disconnected"
    val deviceInfo = deviceData[bleId] ?: emptyMap()
    val distance = deviceInfo["Distance"] ?: "N/A"
    val battery = deviceInfo["Battery"] ?: "N/A"
    val tagData = tagDataMap[bleId]

    LaunchedEffect(bleId) {
        trackerViewModel.getTrackerByBleId(bleId)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Device Details",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            tracker?.let { tracker ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Name: ${tracker.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "BLE ID: ${tracker.ble_id}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Type: ${tracker.type}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Status: $connectionStatus",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Distance: $distance",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Battery: $battery",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        tracker.last_connection?.let {
                            Text(
                                text = "Last Connection: $it",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        tracker.last_connection?.let {
                            Text(
                                text = "Last latitude: $tracker.last_latitude",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        tracker.last_connection?.let {
                            Text(
                                text = "Last longitude: ${tracker.last_longitude}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } ?: run {
                Text(
                    text = "No tracker found for BLE ID: $bleId",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Back")
            }
        }
    }
}