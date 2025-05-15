package com.example.atlas.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.atlas.models.Tracker
import com.example.atlas.ui.viewmodel.TrackerViewModel
import com.example.atlas.ui.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavHostController,
    isAdvancedMode: MutableState<Boolean>,
    connectionStates: SnapshotStateMap<String, String>,
    onDisconnect: (String) -> Unit,
    userViewModel: UserViewModel = hiltViewModel(),
    trackerViewModel: TrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by userViewModel.user.collectAsStateWithLifecycle()
    val isAuthenticated by userViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val error by userViewModel.error.collectAsStateWithLifecycle()
    val trackers by trackerViewModel.getTrackersByUserId(user?.id ?: "").collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    // Check session on entry
    LaunchedEffect(Unit) {
        Log.d("ProfileScreen", "Initial isAuthenticated: $isAuthenticated")
        try {
            val hasSession = userViewModel.checkUserSession()
            Log.d("ProfileScreen", "Session check result: hasSession=$hasSession")
            if (!hasSession) {
                Log.d("ProfileScreen", "No active session, navigating to logIn")
                navController.navigate("logIn") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileScreen", "Error checking session: ${e.localizedMessage}", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // User info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            if (user == null && error == null) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
            } else user?.let {
                Text(
                    text = "Name: ${user!!.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Email: ${user!!.email}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Display error message if any
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // My Devices header
            Text(
                text = "My Devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Scrollable device list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp)
            ) {
                items(trackers) { tracker ->
                    // State for owner name, default to "Unknown"
                    var ownerName by remember(tracker.user1) { mutableStateOf("Unknown") }
                    // Fetch owner name asynchronously
                    tracker.user1?.let { userId ->
                        LaunchedEffect(userId) {
                            try {
                                val user = userViewModel.findUserById(userId)
                                ownerName = user?.name ?: "Unknown"
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Failed to fetch owner name for userId: $userId", e)
                                ownerName = "Unknown"
                            }
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Device: ${tracker.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Owner: $ownerName",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Buttons at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Log Off Button
            Button(
                onClick = {
                    Log.d("ProfileScreen", "Logging out user and disconnecting BLE devices")
                    // Disconnect all connected BLE devices
                    connectionStates.filter { it.value == "Connected" }.keys.forEach { address ->
                        Log.d("ProfileScreen", "Disconnecting device: $address")
                        onDisconnect(address)
                    }
                    userViewModel.logout(context)
                    navController.navigate("logIn") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Log Off")
            }

            // Change User Data Button
            Button(
                onClick = {
                    Log.d("ProfileScreen", "Navigating to ChangeDataScreen")
                    navController.navigate("changeData")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Change Data")
            }
        }
    }
}