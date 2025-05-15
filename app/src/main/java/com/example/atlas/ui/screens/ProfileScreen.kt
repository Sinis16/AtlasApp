package com.example.atlas.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.atlas.models.Tracker
import com.example.atlas.ui.viewmodel.TrackerViewModel
import com.example.atlas.ui.viewmodel.UserViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

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

    // Read dark mode preference from SharedPreferences
    val isDarkMode by remember {
        derivedStateOf {
            context.getSharedPreferences("AtlasPrefs", Context.MODE_PRIVATE)
                .getBoolean("isDarkMode", true) // Default to true since you're using dark mode
        }
    }

    // Log dark mode state
    LaunchedEffect(isDarkMode) {
        Log.d("ProfileScreen", "isDarkMode: $isDarkMode")
    }

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

    // Log tracker names for debugging
    LaunchedEffect(trackers) {
        trackers.forEach { tracker ->
            Log.d("ProfileScreen", "Tracker ID: ${tracker.id}, Name: ${tracker.name}")
        }
    }

    // Custom MaterialTheme to override AtlasTheme
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF424242), // Dark gray
            onPrimary = Color.White,
            surface = Color(0xFF121212),
            onSurface = Color.White
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isDarkMode) Color(0xFF121212) else Color.White, // Dark mode background
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // User info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (user == null && error == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else user?.let {
                            Text(
                                text = "Name: ${user!!.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White, // Ensure readability in dark mode
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Email: ${user!!.email}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
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

                        // Buttons section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Log Off Button
                                Button(
                                    onClick = {
                                        Log.d("ProfileScreen", "Logging out user and disconnecting BLE devices")
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
                                    modifier = Modifier.size(width = 200.dp, height = 36.dp)
                                ) {
                                    Text("Log out")
                                }

                                // Change Data Button
                                Button(
                                    onClick = { navController.navigate("changeData") },
                                    modifier = Modifier.size(width = 200.dp, height = 36.dp)
                                ) {
                                    Text("Change data")
                                }
                            }
                        }

                        // My Devices header
                        Text(
                            text = "My Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Scrollable device list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E1E1E) // Dark card background
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Device: ${tracker.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Owner: $ownerName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}