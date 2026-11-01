package com.filestech.callhalt.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.ui.theme.OnStop
import com.filestech.callhalt.ui.theme.StopBackground
import kotlinx.coroutines.delay

/** "Continue" stays disabled this long: a pause at the right moment is the whole point (PROJET.md, § 3). */
const val CONTINUE_DELAY_SECONDS = 10

/**
 * The STOP screen (PROJET.md, § 3). It blocks nothing: it is a pause, with the one check that holds on the
 * phone — hang up, then call the bank yourself.
 *
 * The same composable serves the demonstration ([demo] = true, nothing happened) and, once the adapters
 * exist, the real interruption shown by its own Activity — never an overlay window, which a banking app
 * hides (docs/ARCHITECTURE.md, D-006). What each choice DOES (hang up, dial the bank once the call has
 * ended) belongs to that host, not to this screen.
 */
@Composable
fun StopScreen(demo: Boolean, onAnswer: (StopChoice) -> Unit) {
    // Saved across a rotation: turning the phone must not restart the wait, nor end it.
    var secondsLeft by rememberSaveable { mutableIntStateOf(CONTINUE_DELAY_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(ONE_SECOND_MILLIS)
            secondsLeft--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StopBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (demo) {
            Text(text = stringResource(R.string.stop_demo_banner), color = OnStop, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.stop_title),
            color = OnStop,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(text = stringResource(R.string.stop_on_call), color = OnStop, style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.stop_rule), color = OnStop, style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(R.string.stop_advice), color = OnStop, style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = { onAnswer(StopChoice.HANG_UP) },
            colors = ButtonDefaults.buttonColors(containerColor = OnStop, contentColor = StopBackground),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.stop_hang_up), modifier = Modifier.padding(vertical = 8.dp))
        }
        OutlinedButton(
            onClick = { onAnswer(StopChoice.CALL_MY_BANK) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStop),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.stop_call_my_bank), modifier = Modifier.padding(vertical = 8.dp))
        }
        TextButton(
            onClick = { onAnswer(StopChoice.CONTINUE) },
            enabled = secondsLeft == 0,
            colors = ButtonDefaults.textButtonColors(contentColor = OnStop, disabledContentColor = OnStop.copy(alpha = DISABLED_ALPHA)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (secondsLeft == 0) {
                    stringResource(R.string.stop_continue)
                } else {
                    pluralStringResource(R.plurals.stop_continue_in, secondsLeft, secondsLeft)
                },
            )
        }
    }
}

private const val ONE_SECOND_MILLIS = 1_000L
private const val DISABLED_ALPHA = 0.6f
