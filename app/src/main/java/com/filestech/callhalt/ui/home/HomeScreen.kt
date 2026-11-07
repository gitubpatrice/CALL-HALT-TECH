package com.filestech.callhalt.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.filestech.callhalt.platform.access.AccessSettings
import com.filestech.callhalt.ui.guardian.GuardianMove
import com.filestech.callhalt.ui.guardian.GuardianState
import com.filestech.callhalt.ui.guardian.GuardianViewModel
import com.filestech.callhalt.ui.guardian.guardianLabel
import com.filestech.callhalt.ui.protection.ProtectionState
import com.filestech.callhalt.ui.protection.ProtectionStatus
import com.filestech.callhalt.ui.protection.ProtectionViewModel
import com.filestech.callhalt.ui.protection.accessText
import com.filestech.callhalt.ui.theme.brandButtonColors

/**
 * The home screen: the state of the protection (docs/ARCHITECTURE.md, D-022), read from the phone each time the app
 * comes to the front — on only when every access is granted, and then it says what it watches, no more (a protection
 * app that looks active while it is not would be worse than none); weakened while storage fails (P-10). Then who is
 * texted when a STOP shows (D-034), the rule, and the STOP demonstration (PROJET.md, § 4.9).
 */
@Composable
fun HomeScreen(
    onActivate: () -> Unit,
    onTryStop: () -> Unit,
    onGuardian: () -> Unit,
    viewModel: ProtectionViewModel = hiltViewModel(),
    guardianViewModel: GuardianViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val guardian by guardianViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        guardianViewModel.on(GuardianMove.Resumed)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.leave() }

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
            GuardianSection(guardian, onGuardian)
            Text(text = stringResource(R.string.home_rule), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onTryStop, colors = brandButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.home_try_stop), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ProtectionCard(state: ProtectionState, onActivate: () -> Unit) {
    val on = state.status == ProtectionStatus.ON
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
                text = stringResource(
                    when (state.status) {
                        ProtectionStatus.ON -> R.string.home_on
                        ProtectionStatus.NO_ALERT, ProtectionStatus.STORAGE_FAILING, ProtectionStatus.ALERT_ONLY ->
                            R.string.home_weakened
                        ProtectionStatus.OFF, ProtectionStatus.NOT_LISTENING, ProtectionStatus.UNSUPPORTED -> R.string.home_off
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            when (state.status) {
                ProtectionStatus.ON ->
                    Text(text = watched(state.afterCallMinutes), style = MaterialTheme.typography.bodyLarge)
                ProtectionStatus.NO_ALERT -> {
                    Text(text = stringResource(R.string.home_weakened_alerts), style = MaterialTheme.typography.bodyLarge)
                    CardButton(R.string.home_activate, onActivate)
                }
                // No button: nothing the user can grant would do better on this phone (D-030). Not what it watches either:
                // that text says the STOP screen shows, which it does not here by itself.
                ProtectionStatus.ALERT_ONLY ->
                    Text(text = stringResource(R.string.home_weakened_screen), style = MaterialTheme.typography.bodyLarge)
                // The app asked the phone to bind it again already: turning the access off and on is what is left.
                ProtectionStatus.NOT_LISTENING -> {
                    val context = LocalContext.current
                    Text(text = stringResource(R.string.home_not_listening), style = MaterialTheme.typography.bodyLarge)
                    CardButton(R.string.access_open_settings) {
                        AccessSettings.open(context, AccessSettings.notifications(context))
                    }
                }
                ProtectionStatus.STORAGE_FAILING -> {
                    val context = LocalContext.current
                    Text(text = stringResource(R.string.home_weakened_detail), style = MaterialTheme.typography.bodyLarge)
                    CardButton(R.string.home_open_storage) { AccessSettings.open(context, AccessSettings.storage()) }
                }
                // No button: it would lead to a settings page with no switch.
                ProtectionStatus.UNSUPPORTED ->
                    Text(text = stringResource(R.string.home_unsupported), style = MaterialTheme.typography.bodyLarge)
                ProtectionStatus.OFF -> {
                    Text(
                        text = pluralStringResource(R.plurals.home_missing, state.missing.size, state.missing.size),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CardButton(R.string.home_activate, onActivate)
                }
            }
            // What it does not see, wherever it sees something: on, or weakened but working. The landline first — the
            // scam that needs no call on this phone (E-1) — then the limit, which ends on "the rule below".
            if (state.status in WATCHING) {
                // First: a hole the user can close, in the SMS app's settings (FN-2).
                if (state.smsPreviewHidden) {
                    Text(
                        text = stringResource(R.string.home_sms_preview_hidden),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(text = stringResource(R.string.home_on_landline), style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(R.string.home_on_limit), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The states in which the protection sees something: on, or weakened but working. */
private val WATCHING = setOf(
    ProtectionStatus.ON,
    ProtectionStatus.NO_ALERT,
    ProtectionStatus.STORAGE_FAILING,
    ProtectionStatus.ALERT_ONLY,
)

@Composable
private fun CardButton(@StringRes label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, colors = brandButtonColors(), modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(label), modifier = Modifier.padding(vertical = 8.dp))
    }
}

/**
 * The relative (D-034): who is texted, or that no one is; and when the permission was taken back, that the text cannot
 * go — said here, where the user looks, not only on the relative's screen. Not the protection's state: without a
 * relative the STOP screen protects all the same.
 */
@Composable
private fun GuardianSection(state: GuardianState, onGuardian: () -> Unit) {
    val guardian = state.guardian
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.home_guardian_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = guardian?.let { stringResource(R.string.home_guardian_set, guardianLabel(it)) }
                ?: stringResource(R.string.home_guardian_none),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (guardian != null && !state.canSend) {
            Text(
                text = stringResource(R.string.home_guardian_cannot_send),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = onGuardian, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(if (guardian == null) R.string.home_guardian_choose else R.string.home_guardian_change))
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

/**
 * Each access with its state in words, not in colour alone; the state under the title, not beside it: at a font of
 * 200 %, beside it left the title too narrow for its words, cut in the middle ("scherm/ata", U1).
 */
@Composable
private fun AccessList(state: ProtectionState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.applicable.forEach { access ->
            val granted = access in state.granted
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = stringResource(accessText(access).title),
                    style = MaterialTheme.typography.bodyLarge,
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
