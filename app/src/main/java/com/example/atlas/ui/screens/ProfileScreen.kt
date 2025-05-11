package com.example.atlas.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.atlas.ui.viewmodel.UserViewModel

@Composable
fun ProfileScreen(navController: NavHostController, userViewModel: UserViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val user by userViewModel.user.collectAsStateWithLifecycle()
    val isAuthenticated by userViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val error by userViewModel.error.collectAsStateWithLifecycle()

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
        // Spacer to push content toward center
        Spacer(modifier = Modifier.weight(1f))

        // Loading indicator or user data
        if (user == null && error == null) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
        } else user?.let {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Name: ${user!!.name}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Email: ${user!!.email}",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
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

        // Spacer to push buttons to bottom
        Spacer(modifier = Modifier.weight(1f))

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
                    Log.d("ProfileScreen", "Logging out user")
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