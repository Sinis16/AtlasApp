package com.example.atlas.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun BottomNavBar(navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Left spacer (for future left icon)
        NavigationBarItem(
            icon = { /* Empty for now */ },
            selected = false,
            onClick = { /* Placeholder */ },
            enabled = false,
            modifier = Modifier.weight(1f)
        )

        // Center: Home
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") }, // Remove this for icon-only
            selected = navController.currentDestination?.route == "home",
            onClick = {
                navController.navigate("home") {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        // Right: Connect (SettingsInputComponent)
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Menu, contentDescription = "Connect") },
            label = { Text("Connect") }, // Remove this for icon-only
            selected = navController.currentDestination?.route == "connection",
            onClick = {
                navController.navigate("connection") {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
    }
}