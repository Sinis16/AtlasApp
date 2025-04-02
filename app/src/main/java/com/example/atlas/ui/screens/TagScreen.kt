package com.example.atlas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.atlas.models.TagData

@Composable
fun TagScreen(navController: NavController, tagId: String) {
    val tagData = remember {
        when (tagId) {
            "tag1" -> TagData(id = "tag1", distance = 2.5, angle = 45.0, battery = 90)
            "tag2" -> TagData(id = "tag2", distance = 3.8, angle = 120.0, battery = 75)
            "tag3" -> TagData(id = "tag3", distance = 1.2, angle = 210.0, battery = 60)
            else -> TagData(id = tagId, distance = 0.0, angle = 0.0, battery = 0)
        }
    }

    var isSoundPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tag: $tagId",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        InfoCard(label = "Distance", value = "${"%.1f".format(tagData.distance)} cm")
        InfoCard(label = "Angle", value = "${"%.1f".format(tagData.angle)}°")
        InfoCard(label = "Battery", value = "${tagData.battery}%")

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                isSoundPlaying = !isSoundPlaying
                // TODO: Implement sound emission logic
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSoundPlaying) "Stop Sound" else "Play Sound")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Home")
        }
    }
}