package com.example.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.atlas.screens.CardConnectionScreen
import com.example.atlas.screens.HomeScreen
import com.example.atlas.screens.LogInScreen
import com.example.atlas.screens.RegisterScreen
import com.example.atlas.screens.TagScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "logIn") {
        composable("home") { HomeScreen(navController) }
        composable("connection") { CardConnectionScreen(navController) }
        composable("logIn") { LogInScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("tag/{tagId}") { backStackEntry ->
            val tagId = backStackEntry.arguments?.getString("tagId") ?: ""
            TagScreen(navController, tagId)
        }
    }
}
