package com.example.atlas.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.atlas.blescanner.BleScanManager
import com.example.atlas.blescanner.model.BleDevice
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.ui.screens.CardConnectionScreen
import com.example.atlas.ui.screens.HomeScreen
import com.example.atlas.ui.screens.LogInScreen
import com.example.atlas.ui.screens.RegisterScreen
import com.example.atlas.ui.screens.TagScreen

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppNavHost(
    navController: NavHostController,
    permissionManager: PermissionManager,
    bleScanManager: BleScanManager,
    foundDevices: MutableList<BleDevice>,
    connectionStates: SnapshotStateMap<String, String>,
    onConnect: (String) -> Unit
) {
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
            CardConnectionScreen(
                navController = navController,
                permissionManager = permissionManager,
                bleScanManager = bleScanManager,
                foundDevices = foundDevices,
                connectionStates = connectionStates,
                onConnect = onConnect
            )
        }
        composable(
            route = "tag/{tagId}",
            arguments = listOf(
                navArgument("tagId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val tagId = backStackEntry.arguments?.getString("tagId") ?: ""
            TagScreen(navController = navController, tagId = tagId)
        }
    }
}