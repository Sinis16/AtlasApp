package com.example.atlas.screens

import androidx.compose.foundation.clickable
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

@Composable
fun HomeScreen(navController: NavController) {
    // Mock Bluetooth device data
    var battery by remember { mutableStateOf(78) }  // Bluetooth device battery
    var latency by remember { mutableStateOf(20) }  // Latency in ms

    // Mock list of UWB tags
    val trackedTags = remember {
        mutableStateListOf(
            TagData(id = "tag1", distance = 2.5, angle = 45.0, battery = 90),
            TagData(id = "tag2", distance = 3.8, angle = 120.0, battery = 75),
            TagData(id = "tag3", distance = 1.2, angle = 210.0, battery = 60)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Device Status", fontSize = 24.sp, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(10.dp))

        InfoCard(label = "Battery", value = "$battery%")
        InfoCard(label = "Latency", value = "$latency ms")

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Tracked Tags", fontSize = 20.sp)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(trackedTags) { tag ->
                TagCard(tag) { navController.navigate("tag/${tag.id}") }
            }
        }
    }
}

@Composable
fun TagCard(tag: TagData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Tag: ${tag.id}", fontSize = 18.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Distance: ${tag.distance} m", fontSize = 16.sp)
                Text(text = "Angle: ${tag.angle}°", fontSize = 16.sp)
                Text(text = "Battery: ${tag.battery}%", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 18.sp)
            Text(text = value, fontSize = 18.sp)
        }
    }
}

data class TagData(val id: String, val distance: Double, val angle: Double, val battery: Int)
