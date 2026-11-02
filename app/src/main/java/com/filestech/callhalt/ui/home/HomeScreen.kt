package com.filestech.callhalt.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.callhalt.R
import com.filestech.callhalt.platform.access.Access
import com.filestech.callhalt.ui.protection.ProtectionState
import com.filestech.callhalt.ui.protection.ProtectionViewModel
import com.filestech.callhalt.ui.protection.accessText
import com.filestech.callhalt.ui.theme.brandButtonColors

/**
 * The home screen: the state of the protection (docs/ARCHITECTURE.md, D-022), read from the phone each time the app
 * comes to the front — on only when every access is granted, and then it says what it watches, no more (a protection
 * app that looks active while it is not would be worse than none). Then the rule, and the STOP demonstration
 * (PROJET.md, § 4.9).
 */
@Composable
fun HomeScreen(onActivate: () -> Unit, onTryStop: () -> Unit, viewModel: ProtectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            ProtectionCard(state, onActivate)
            AccessList(state)
            Text(text = stringResource(R.string.home_rule), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onTryStop, colors = brandButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_try_stop), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ProtectionCard(state: ProtectionState, onActivate: () -> Unit) {
    val on = state.missing.isEmpty()
    val colors = if (on) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(if (on) R.string.home_on else R.string.home_off),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (on) {
                Text(text = watched(state.afterCallMinutes), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = pluralStringResource(R.plurals.home_missing, state.missing.size, state.missing.size),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onActivate, colors = brandButtonColors(), modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.home_activate), modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/** No "0 minutes after": a window of zero is a protection during the call alone. */
@Composable
private fun watched(afterCallMinutes: Long): String = if (afterCallMinutes == 0L) {
    stringResource(R.string.home_on_detail_call_only)
} else {
    pluralStringResource(R.plurals.home_on_detail, afterCallMinutes.toInt(), afterCallMinutes.toInt())
}

/** Each access with its state in words, not in colour alone. */
@Composable
private fun AccessList(state: ProtectionState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Access.entries.forEach { access ->
            val granted = access in state.granted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(accessText(access).title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (granted) R.string.access_granted else R.string.access_missing),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
