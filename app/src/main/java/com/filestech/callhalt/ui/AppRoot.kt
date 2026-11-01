package com.filestech.callhalt.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filestech.callhalt.ui.home.HomeScreen
import com.filestech.callhalt.ui.stop.StopScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                // Single top: a double tap opens one demonstration, not two stacked.
                onTryStop = { navController.navigate(Routes.STOP_DEMO) { launchSingleTop = true } },
            )
        }
        composable(Routes.STOP_DEMO) {
            StopScreen(
                demo = true,
                // Pops the demonstration only if it is still there: a second tap during the exit
                // transition must not pop Home and leave an empty screen.
                onAnswer = { navController.popBackStack(Routes.STOP_DEMO, inclusive = true) },
            )
        }
    }
}
