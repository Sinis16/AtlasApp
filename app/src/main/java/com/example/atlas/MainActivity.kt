package com.example.atlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.atlas.navigation.AppNavHost
import com.example.atlas.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AtlasTheme {
                val navController = rememberNavController()

                // Mocked states for now, replace with actual logic
                var isLoggedIn by remember { mutableStateOf(false) }
                var isConnected by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // First check login status
                    if (!isLoggedIn) {
                        navController.navigate("login") {
                            popUpTo(0)  // Clear stack
                        }
                    } else if (!isConnected) {
                        navController.navigate("connection") {
                            popUpTo(0)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(navController)
                }
            }
        }
    }
}
