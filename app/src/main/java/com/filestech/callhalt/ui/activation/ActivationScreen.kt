package com.filestech.callhalt.ui.activation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.app.ActivityCompat
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
    val activity = LocalActivity.current
    val text = accessText(access)
    // The phone stops showing its dialog once refused twice (or "don't ask again"): the request then answers "refused"
    // at once. Only the settings can grant it from there. Saved: a rotation must not bring back a button that does nothing.
    var noLongerAsked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        onAnswered()
        noLongerAsked = activity != null && results.values.any { granted -> !granted } &&
            CALL_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
    val asksInApp = access == Access.CALLS && !noLongerAsked

    Surface(modifier = Modifier.fillMaxSize()) {
        // The texts scroll; the buttons stay at the bottom, in sight whatever the size of the font.
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
                Text(
                    text = stringResource(if (noLongerAsked) R.string.access_calls_refused else text.how),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (mayBeRestricted) {
                    RestrictedSettingsHelp(onOpenAppInfo = { AccessSettings.open(context, AccessSettings.appInfo(context)) })
                }
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
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.access_later))
            }
        }
    }
}

/**
 * The way through the phone's "restricted settings" (measured, docs/ARCHITECTURE.md, P-3): the menu that allows them
 * shows in the app info only once the phone has refused once — hence the order of the steps.
 */
@Composable
private fun RestrictedSettingsHelp(onOpenAppInfo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.restricted_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.restricted_why), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.restricted_steps), style = MaterialTheme.typography.bodyLarge)
            // Scammers have their victims allow the restricted settings of THEIR app.
            Text(
                text = stringResource(R.string.restricted_warning),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.restricted_open_app_info))
            }
        }
    }
}

private fun settingsPages(access: Access, context: Context): List<Intent> = when (access) {
    Access.CALLS -> AccessSettings.appInfo(context)
    Access.SCREEN -> AccessSettings.screen(context)
    Access.NOTIFICATIONS -> AccessSettings.notifications(context)
}
