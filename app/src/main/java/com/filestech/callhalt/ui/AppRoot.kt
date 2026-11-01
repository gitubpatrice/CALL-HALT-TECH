package com.filestech.callhalt.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.CallDirection
import com.filestech.callhalt.domain.model.CallSession
import com.filestech.callhalt.domain.model.Counterpart
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.ui.home.HomeScreen
import com.filestech.callhalt.ui.stop.StopScreen
import java.time.Instant

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
                // The case the method was built on: a code arrives while the scammer is on the line.
                trigger = SensitiveActionKind.CODE_MESSAGE,
                call = DEMO_CALL,
                demo = true,
                // Pops the demonstration only if it is still there: a second tap during the exit
                // transition must not pop Home and leave an empty screen.
                onAnswer = { navController.popBackStack(Routes.STOP_DEMO, inclusive = true) },
            )
        }
    }
}

/** A cellular call in progress, from a number the phone could not read: nothing of it is shown but its state. */
private val DEMO_CALL = CallSession(CallChannel.CELLULAR, CallDirection.INCOMING, Counterpart.Unreadable, startedAt = Instant.EPOCH)
