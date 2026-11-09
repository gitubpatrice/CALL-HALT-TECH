package com.filestech.callhalt.ui.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.callhalt.R
import timber.log.Timber

/**
 * The history of the alerts (PROJET.md, function 8; publication audit, #1; D-041; Patrice, 2026-11-09): each STOP screen
 * with its answer, each warning, the newest first; and "Share", which hands the whole history, as text, to the app the
 * user picks in Android's share menu (a text, an e-mail…) — for the bank or the police: an operation contested within
 * 13 months, the journal kept 14. Nothing leaves the phone but by that touch.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Built again when the language or the zone may have changed (a configuration change).
    val text = remember(LocalConfiguration.current) { HistoryText(context.resources) }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            val shown = alerts
            when {
                // Still reading: nothing said yet, rather than "no alert" for an instant.
                shown == null -> Unit
                shown.isEmpty() -> item {
                    Text(text = stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    item {
                        OutlinedButton(onClick = { share(context, text.shared(shown)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.history_share))
                        }
                    }
                    items(shown) { alert -> AlertCard(text.lines(alert)) }
                }
            }
        }
    }
}

/** One alert: its time, then what it was and what was chosen. */
@Composable
private fun AlertCard(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        // Read as one by TalkBack: the time alone, then each line apart, said nothing (review of 2026-11-09, 7).
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = lines.first(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            lines.drop(1).forEach { line -> Text(text = line, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/**
 * Android's share menu, with the history as plain text: the user picks where it goes, or nothing. A history of thousands
 * of alerts would pass the size of what one app may hand another (about 1 MB): the phone refuses, the app goes on
 * (review of 2026-11-09, 4; D-041, a limit).
 */
// Any failure of the phone's own: ActivityNotFoundException, or a RuntimeException around TransactionTooLargeException.
@Suppress("TooGenericExceptionCaught")
private fun share(context: Context, history: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.history_share_heading))
        .putExtra(Intent.EXTRA_TEXT, history)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (refused: RuntimeException) {
        Timber.w(refused, "History not shared (%d characters)", history.length)
    }
}
