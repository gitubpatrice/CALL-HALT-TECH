package com.filestech.callhalt.ui.stop

import androidx.annotation.StringRes
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
import com.filestech.callhalt.domain.model.CallChannel
import com.filestech.callhalt.domain.model.SensitiveActionKind
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.ui.theme.OnStop
import com.filestech.callhalt.ui.theme.StopBackground
import kotlinx.coroutines.delay

/** "Continue" stays disabled this long: a pause at the right moment is the whole point (PROJET.md, § 3). */
const val CONTINUE_DELAY_SECONDS = 10

/**
 * The STOP screen (PROJET.md, § 3). It blocks nothing: it is a pause, with the one check that holds on the
 * phone — hang up, then call back yourself on a number you know. Its words come from [stopText]: what just
 * happened ([trigger]), whether the call is still going on ([onCall]) and on which [channel], and whether the
 * app may end it ([canEndCall]).
 *
 * The same composable serves the demonstration ([demo] = true, nothing happened) and, once the adapters
 * exist, the real interruption shown by its own Activity — never an overlay window, which a banking app
 * hides (docs/ARCHITECTURE.md, D-006). What each choice DOES (hang up, dial the bank once the call has
 * ended) belongs to that host, not to this screen.
 *
 * The texts scroll; the buttons stay at the bottom, in sight whatever the length of the texts and the size
 * of the font.
 */
@Composable
fun StopScreen(
    trigger: SensitiveActionKind,
    onCall: Boolean,
    channel: CallChannel,
    canEndCall: Boolean,
    demo: Boolean,
    onAnswer: (StopChoice) -> Unit,
) {
    val text = stopText(trigger, onCall, channel, canEndCall)
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // In order of importance: what does not fit scrolls, and someone under pressure does not scroll. On the
        // S9 at the default font size (2026-11-01), the texts after the rule were out of sight.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(text.situation), color = OnStop, style = MaterialTheme.typography.titleLarge)
                Text(text = stringResource(text.happened), color = OnStop, style = MaterialTheme.typography.titleLarge)
            }
            // The scammer is still on the line and will say "that is normal, press Continue": this comes first.
            Text(
                text = stringResource(R.string.stop_ignore_warning),
                color = OnStop,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(text = stringResource(text.rule), color = OnStop, style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.stop_spoofed_number), color = OnStop, style = MaterialTheme.typography.bodyLarge)
            // Last: the buttons below already say it.
            Text(text = stringResource(text.advice), color = OnStop, style = MaterialTheme.typography.bodyLarge)
        }

        // The first button is the safe way out: hang up while the app can, otherwise call the bank — and neither
        // during a call the app cannot end (the advice then says to hang up).
        val callMyBank = text.callMyBank
        if (text.offerHangUp) {
            StopFilledButton(R.string.stop_hang_up) { onAnswer(StopChoice.HANG_UP) }
            callMyBank?.let { label ->
                OutlinedButton(
                    onClick = { onAnswer(StopChoice.CALL_MY_BANK) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStop),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StopButtonLabel(label)
                }
            }
        } else {
            callMyBank?.let { label -> StopFilledButton(label) { onAnswer(StopChoice.CALL_MY_BANK) } }
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
                // Centred like the buttons above when a large font wraps it (S9, font size 1.3).
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StopFilledButton(@StringRes label: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = OnStop, contentColor = StopBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StopButtonLabel(label)
    }
}

@Composable
private fun StopButtonLabel(@StringRes label: Int) {
    Text(text = stringResource(label), modifier = Modifier.padding(vertical = 8.dp))
}

private const val ONE_SECOND_MILLIS = 1_000L
private const val DISABLED_ALPHA = 0.6f
