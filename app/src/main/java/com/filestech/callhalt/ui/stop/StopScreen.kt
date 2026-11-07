package com.filestech.callhalt.ui.stop

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.layout.layoutId
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
 * ended, open the accessibility settings) belongs to that host, not to this screen.
 *
 * Laid out by [StopLayout]: the buttons at the bottom when all fits; otherwise the whole screen scrolls, the buttons
 * right under the warning, so that they are in sight whatever the length of the texts and the size of the font.
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

    // In order of importance, and in sight: StopLayout keeps the buttons at the bottom when all fits, and otherwise
    // brings them up under the warning, the explanation after them (U2). On the S9 at the default font size
    // (2026-11-01), the texts after the rule were out of sight; at 200 %, all after "If you are told to ignore".
    StopLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(StopBackground)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        if (demo) {
            Text(
                text = stringResource(R.string.stop_demo_banner),
                color = OnStop,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.layoutId(StopPart.MESSAGE),
            )
        }
        Text(
            text = stringResource(R.string.stop_title),
            color = OnStop,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .layoutId(StopPart.MESSAGE)
                .fillMaxWidth()
                .semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.layoutId(StopPart.MESSAGE)) {
            Text(text = stringResource(text.situation), color = OnStop, style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(text.happened), color = OnStop, style = MaterialTheme.typography.titleLarge)
        }
        // The scammer is still on the line and will say "that is normal, press Continue": this comes first.
        Text(
            text = stringResource(R.string.stop_ignore_warning),
            color = OnStop,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.layoutId(StopPart.MESSAGE),
        )
        StopExplanation(text.rule)
        StopExplanation(R.string.stop_spoofed_number)
        // Last of the texts: the buttons already say it.
        StopExplanation(text.advice)

        // The safe ways out (StopText.buttons), the most protective first and filled; none during a call the app
        // cannot end (the advice then says to hang up).
        text.buttons.forEachIndexed { index, button ->
            if (index == 0) {
                StopFilledButton(button.label) { onAnswer(button.choice) }
            } else {
                OutlinedButton(
                    onClick = { onAnswer(button.choice) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStop),
                    // The default outline, a pale grey, barely shows on the red (U3, WCAG 1.4.11): the text's colour.
                    border = BorderStroke(2.dp, OnStop),
                    modifier = Modifier
                        .layoutId(StopPart.BUTTON)
                        .fillMaxWidth(),
                ) {
                    StopButtonLabel(button.label)
                }
            }
        }
        TextButton(
            onClick = { onAnswer(StopChoice.CONTINUE) },
            enabled = secondsLeft == 0,
            colors = ButtonDefaults.textButtonColors(contentColor = OnStop, disabledContentColor = OnStop.copy(alpha = DISABLED_ALPHA)),
            modifier = Modifier
                .layoutId(StopPart.CONTINUE)
                .fillMaxWidth(),
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
private fun StopExplanation(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        color = OnStop,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.layoutId(StopPart.EXPLANATION),
    )
}

@Composable
private fun StopFilledButton(@StringRes label: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = OnStop, contentColor = StopBackground),
        modifier = Modifier
            .layoutId(StopPart.BUTTON)
            .fillMaxWidth(),
    ) {
        StopButtonLabel(label)
    }
}

@Composable
private fun StopButtonLabel(@StringRes label: Int) {
    // Centred when a large font wraps it, as "Continue" (200 %, 2026-11-07).
    Text(text = stringResource(label), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
}

private const val ONE_SECOND_MILLIS = 1_000L
private const val DISABLED_ALPHA = 0.6f
