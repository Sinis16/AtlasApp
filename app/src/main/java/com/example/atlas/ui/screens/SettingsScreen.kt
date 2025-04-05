package com.example.atlas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun SettingsScreen(
    navController: NavHostController,
    updateRate: MutableState<Long>, // Pass update rate state
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                UpdateRateSetting(updateRate)
            }
        }
    }
}

@Composable
fun UpdateRateSetting(updateRate: MutableState<Long>) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "Fast (100ms)" to 100L,
        "Normal (500ms)" to 500L,
        "Slow (1000ms)" to 1000L
    )
    val currentLabel = options.find { it.second == updateRate.value }?.first ?: "Normal (500ms)"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Column {
            Text(text = "Update Rate", fontSize = 16.sp)
            Text(text = currentLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                options.forEach { (label, value) ->
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                updateRate.value = value
                                expanded = false
                            }
                    )
                }
            }
        }
    }
}