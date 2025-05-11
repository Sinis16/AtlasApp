package com.example.atlas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atlas.models.TagData

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
                Text(text = "Distance: ${"%.1f".format(tag.distance)} m", fontSize = 16.sp)
                Text(text = "Angle: ${"%.1f".format(tag.angle)}°", fontSize = 16.sp)
                Text(text = "Battery: ${tag.battery}%", fontSize = 16.sp)
            }
        }
    }
}