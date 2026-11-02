package com.filestech.callhalt.ui.activation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.filestech.callhalt.platform.access.AccessSettings
import com.filestech.callhalt.platform.access.CALL_PERMISSIONS
import com.filestech.callhalt.ui.protection.ProtectionViewModel
import com.filestech.callhalt.ui.protection.accessText
import com.filestech.callhalt.ui.theme.brandButtonColors

/**
 * The activation (docs/ARCHITECTURE.md, D-022): one access per step — the first one missing, in [Access]'s order. The
 * accesses are read again each time the screen comes back to the front, so that the one just granted in the phone's
 * settings gives way to the next. Done when none is missing, or on "Later": the home screen then says what is missing.
 */
@Composable
fun ActivationScreen(onDone: () -> Unit, viewModel: ProtectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val access = state.missing.firstOrNull()
    if (access == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // A step of its own for each access: the next one starts at the top, not where the last one was scrolled to
    // (measured on Android 16), and with nothing remembered of it.
    key(access) {
        AccessStep(
            access = access,
            mayBeRestricted = access in state.mayBeRestricted,
            onAnswered = viewModel::refresh,
            onLater = onDone,
        )
    }
}

@Composable
private fun AccessStep(access: Access, mayBeRestricted: Boolean, onAnswered: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val text = accessText(access)
    // The phone stops showing its dialog once refused twice (or "don't ask again"), and the request then answers
    // "refused" at once: only the settings can grant it from there. The app cannot tell that from a dialog closed with
    // Back, or by an incoming call (Android 11+: also "refused", and not counted): after any refusal it offers both,
    // the dialog again and the settings (general review of 2026-11-02, 4). Saved across a rotation.
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        onAnswered()
        if (results.values.any { granted -> !granted }) refused = true
    }
    val asksInApp = access == Access.CALLS

    Surface(modifier = Modifier.fillMaxSize()) {
        // The texts scroll; the buttons stay at the bottom, in sight whatever the size of the font — the way through the
        // restricted settings among them: at a large font, the help is out of sight (general review of 2026-11-02, 3).
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.activation_step, access.ordinal + 1, Access.entries.size),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(text.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(text.why), style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(text.how), style = MaterialTheme.typography.bodyLarge)
                if (asksInApp && refused) {
                    Text(text = stringResource(R.string.access_calls_refused), style = MaterialTheme.typography.bodyLarge)
                }
                if (mayBeRestricted) RestrictedSettingsHelp()
            }
            Button(
                onClick = {
                    if (asksInApp) ask.launch(CALL_PERMISSIONS) else AccessSettings.open(context, settingsPages(access, context))
                },
                colors = brandButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (asksInApp) R.string.access_allow else R.string.access_open_settings),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            when {
                asksInApp && refused -> SecondButton(R.string.access_open_settings) {
                    AccessSettings.open(context, AccessSettings.appInfo(context))
                }
                mayBeRestricted -> SecondButton(R.string.restricted_open_app_info) {
                    AccessSettings.open(context, AccessSettings.appInfo(context))
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.access_later))
            }
        }
    }
}

@Composable
private fun SecondButton(@StringRes label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(label))
    }
}

/**
 * The way through the phone's "restricted settings" (measured, docs/ARCHITECTURE.md, P-3): the menu that allows them
 * shows in the app info only once the phone has refused once — hence the order of the steps. Its button is pinned with
 * the others.
 */
@Composable
private fun RestrictedSettingsHelp() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.restricted_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // Scammers have their victims allow the restricted settings of THEIR app: before the steps, which someone
            // guided on the phone follows without reading on (security review of 2026-11-02, S1).
            Text(
                text = stringResource(R.string.restricted_warning),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(text = stringResource(R.string.restricted_why), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.restricted_steps), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun settingsPages(access: Access, context: Context): List<Intent> = when (access) {
    Access.CALLS -> AccessSettings.appInfo(context)
    Access.SCREEN -> AccessSettings.screen(context)
    Access.NOTIFICATIONS -> AccessSettings.notifications(context)
}
