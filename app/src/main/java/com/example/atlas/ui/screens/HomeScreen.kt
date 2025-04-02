package com.example.atlas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.atlas.models.TagData

@Composable
fun HomeScreen(navController: NavController) {
    var deviceName by remember { mutableStateOf("Card") }
    var battery by remember { mutableStateOf(78) }
    var latency by remember { mutableStateOf(20) }
    var isConnected by remember { mutableStateOf(true) }

    val trackedTags = remember {
        mutableStateListOf(
            TagData(id = "tag1", distance = 2.5, angle = 45.0, battery = 90),
            TagData(id = "tag2", distance = 3.8, angle = 120.0, battery = 75),
            TagData(id = "tag3", distance = 1.2, angle = 210.0, battery = 60)
        )
    }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            navController.navigate("connection") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Device Status",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(10.dp))

        InfoCard(label = "Name", value = deviceName)
        InfoCard(label = "Battery", value = "$battery%")
        InfoCard(label = "Latency", value = "$latency ms")

        Spacer(modifier = Modifier.height(16.dp))

        // Temporary button to test navigation to CardConnectionScreen
        Button(
            onClick = { isConnected = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect (Test)")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Tracked Tags (${trackedTags.size})",
            fontSize = 20.sp,
            style = MaterialTheme.typography.headlineSmall
        )

        if (trackedTags.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No tags detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(trackedTags, key = { it.id }) { tag ->
                    TagCard(tag) { navController.navigate("tag/${tag.id}") }
                }
            }
        }
    }
}