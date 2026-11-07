package com.filestech.callhalt.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.ui.activation.ActivationScreen
import com.filestech.callhalt.ui.guardian.GuardianScreen
import com.filestech.callhalt.ui.home.HomeScreen
import com.filestech.callhalt.ui.stop.StopScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                // Single top: a double tap opens one screen, not two stacked.
                onActivate = { navController.navigate(Routes.ACTIVATION) { launchSingleTop = true } },
                onTryStop = { navController.navigate(Routes.STOP_DEMO) { launchSingleTop = true } },
                onGuardian = { navController.navigate(Routes.GUARDIAN) { launchSingleTop = true } },
            )
        }
        composable(Routes.GUARDIAN) {
            GuardianScreen(onDone = { navController.popBackStack(Routes.GUARDIAN, inclusive = true) })
        }
        composable(Routes.ACTIVATION) {
            // Pops the activation only if it is still there, for the same reason as the demonstration below.
            ActivationScreen(onDone = { navController.popBackStack(Routes.ACTIVATION, inclusive = true) })
        }
        composable(Routes.STOP_DEMO) {
            StopScreen(
                // The case the method was built on: a code arrives while the scammer is on the line — and the
                // full screen, as the app shows it once allowed to end calls.
                trigger = SensitiveActionKind.CODE_MESSAGE,
                onCall = true,
                channel = CallChannel.CELLULAR,
                canEndCall = true,
                demo = true,
                // Pops the demonstration only if it is still there: a second tap during the exit
                // transition must not pop Home and leave an empty screen.
                onAnswer = { navController.popBackStack(Routes.STOP_DEMO, inclusive = true) },
            )
        }
    }
}
