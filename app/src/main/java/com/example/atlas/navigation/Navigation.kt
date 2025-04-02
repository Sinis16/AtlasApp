package com.example.atlas.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.atlas.ui.screens.CardConnectionScreen
import com.example.atlas.ui.screens.HomeScreen
import com.example.atlas.ui.screens.LogInScreen
import com.example.atlas.ui.screens.RegisterScreen
import com.example.atlas.ui.screens.TagScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "logIn") {
        composable("logIn") {
            LogInScreen(navController = navController)
        }
        composable("register") {
            RegisterScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("connection") {
            CardConnectionScreen(navController = navController)
        }
        composable(
            route = "tag/{tagId}",
            arguments = listOf(
                navArgument("tagId") {
                    type = NavType.StringType
                    defaultValue = "" // Optional: Provide a default value
                }
            )
        ) { backStackEntry ->
            val tagId = backStackEntry.arguments?.getString("tagId") ?: ""
            TagScreen(navController = navController, tagId = tagId)
        }
    }
}