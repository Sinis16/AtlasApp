package com.example.atlas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.atlas.models.TagData
import com.example.atlas.models.User
import com.example.atlas.ui.viewmodel.TrackerViewModel
import com.example.atlas.ui.viewmodel.UserViewModel
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.launch

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
    val userViewModel: UserViewModel = hiltViewModel()
    val tracker by trackerViewModel.selectedTracker.collectAsState()
    val connectionStatus = connectionStates[bleId] ?: "Disconnected"
    val deviceInfo = deviceData[bleId] ?: emptyMap()
    val distance = deviceInfo["Distance"] ?: "N/A"
    val battery = deviceInfo["Battery"] ?: "N/A"
    val tagData = tagDataMap[bleId]
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFamilyDialog by remember { mutableStateOf(false) }
    var familyEmail by remember { mutableStateOf("") }
    var showDeleteFamilyDialog by remember { mutableStateOf(false) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var familyMembers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(bleId) {
        trackerViewModel.getTrackerByBleId(bleId)
    }

    // Fetch family member names when opening Delete Family dialog
    LaunchedEffect(showDeleteFamilyDialog, tracker) {
        if (showDeleteFamilyDialog && tracker != null) {
            val members = mutableListOf<Pair<String, String>>()
            tracker!!.user2?.let { id ->
                userViewModel.findUserById(id)?.let { user ->
                    members.add((id to user.name) as Pair<String, String>)
                }
            }
            tracker!!.user3?.let { id ->
                userViewModel.findUserById(id)?.let { user ->
                    members.add((id to user.name) as Pair<String, String>)
                }
            }
            familyMembers = members
            selectedUserId = null // Reset selection
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                            tracker.last_latitude?.let {
                                Text(
                                    text = "Last Latitude: $it",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            tracker.last_longitude?.let {
                                Text(
                                    text = "Last Longitude: $it",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Buttons
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddFamilyDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Family")
                        }
                        Button(
                            onClick = { showDeleteFamilyDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = tracker.user2 != null || tracker.user3 != null
                        ) {
                            Text("Delete Family")
                        }
                        Button(
                            onClick = {
                                // TODO: Implement Change Name
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Change Name: TODO")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Change Name")
                        }
                        Button(
                            onClick = {
                                // TODO: Implement Reset Tag
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Reset Tag: TODO")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Tag")
                        }
                    }
                } ?: run {
                    Text(
                        text = "No tracker found for BLE ID: $bleId",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Add Family Dialog
        if (showAddFamilyDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddFamilyDialog = false
                    familyEmail = ""
                },
                title = { Text("Add Family Member") },
                text = {
                    Column {
                        Text("Enter the email of the family member to add:")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = familyEmail,
                            onValueChange = { familyEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tracker?.let { currentTracker ->
                                coroutineScope.launch {
                                    val user = userViewModel.findUserByEmail(familyEmail)
                                    if (user == null) {
                                        snackbarHostState.showSnackbar("User not found")
                                        showAddFamilyDialog = false
                                        familyEmail = ""
                                        return@launch
                                    }

                                    val updatedTracker = when {
                                        currentTracker.user2 == null -> {
                                            currentTracker.copy(user2 = user.id)
                                        }
                                        currentTracker.user3 == null -> {
                                            currentTracker.copy(user3 = user.id)
                                        }
                                        else -> {
                                            snackbarHostState.showSnackbar("Cannot add more family members")
                                            showAddFamilyDialog = false
                                            familyEmail = ""
                                            return@launch
                                        }
                                    }

                                    trackerViewModel.updateTracker(updatedTracker)
                                    snackbarHostState.showSnackbar("Family member added successfully")
                                    showAddFamilyDialog = false
                                    familyEmail = ""
                                }
                            }
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddFamilyDialog = false
                            familyEmail = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Family Dialog
        if (showDeleteFamilyDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteFamilyDialog = false
                    selectedUserId = null
                    familyMembers = emptyList()
                },
                title = { Text("Delete Family Member") },
                text = {
                    if (familyMembers.isEmpty()) {
                        Text("No family members found.")
                    } else {
                        Column {
                            Text("Select a family member to remove:")
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                            ) {
                                items(familyMembers) { (id, name) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedUserId == id,
                                            onClick = { selectedUserId = id }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tracker?.let { currentTracker ->
                                if (selectedUserId == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Please select a family member")
                                    }
                                    return@TextButton
                                }
                                coroutineScope.launch {
                                    val updatedTracker = when (selectedUserId) {
                                        currentTracker.user2 -> currentTracker.copy(user2 = null)
                                        currentTracker.user3 -> currentTracker.copy(user3 = null)
                                        else -> currentTracker
                                    }
                                    trackerViewModel.updateTracker(updatedTracker)
                                    snackbarHostState.showSnackbar("Family member removed successfully")
                                    showDeleteFamilyDialog = false
                                    selectedUserId = null
                                    familyMembers = emptyList()
                                }
                            }
                        },
                        enabled = familyMembers.isNotEmpty() && selectedUserId != null
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteFamilyDialog = false
                            selectedUserId = null
                            familyMembers = emptyList()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}