package com.filestech.callhalt.ui.activation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.callhalt.R
import com.filestech.callhalt.platform.access.Access
import com.filestech.callhalt.platform.access.AccessSettings
import com.filestech.callhalt.platform.access.runtimePermissions
import com.filestech.callhalt.ui.protection.ProtectionViewModel
import com.filestech.callhalt.ui.protection.RestrictedSettingsHelp
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
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.leave() }

    // Passed once refused: the notification access still comes next. Without it no bank request and no code is seen, and
    // a phone may refuse its battery dialog for good (review of 2026-11-03, 1) — or a Xiaomi read its autostart wrong
    // (another MIUI, HyperOS: not measured; D-025 review, 3). The home screen still says it is missing, and the next
    // activation asks for it again.
    var backgroundPassed by rememberSaveable { mutableStateOf(false) }
    var autostartPassed by rememberSaveable { mutableStateOf(false) }
    val access = state.missing.firstOrNull {
        (it != Access.BACKGROUND || !backgroundPassed) && (it != Access.AUTOSTART || !autostartPassed)
    }
    if (access == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    var shown by rememberSaveable { mutableStateOf(emptyList<Access>()) }
    LaunchedEffect(access) { if (access !in shown) shown = shown + access }
    val (step, steps) = activationStep(access, shown, state.missing, state.applicable)
    // A step of its own for each access: the next one starts at the top, not where the last one was scrolled to
    // (measured on Android 16), and with nothing remembered of it.
    key(access) {
        AccessStep(
            access = access,
            step = step,
            steps = steps,
            mayBeRestricted = access in state.mayBeRestricted,
            onAnswered = viewModel::refresh,
            onLater = onDone,
            onPass = when (access) {
                Access.BACKGROUND -> ({ backgroundPassed = true })
                Access.AUTOSTART -> ({ autostartPassed = true })
                else -> null
            },
        )
    }
}

/**
 * "Step [first] of [second]" for [current]: the steps of THIS activation — those [shown] before it, then it, then the
 * accesses still [missing] after it, in the order of [applicable]. Not the accesses the phone granted already, which no
 * step shows: before Android 13 the alert is granted by default, and the activation ended on "Step 4 of 5" (measured on
 * the Android 11 emulator, 2026-11-04). Nor what does not apply to this phone (D-025, D-030).
 */
internal fun activationStep(current: Access, shown: List<Access>, missing: List<Access>, applicable: List<Access>): Pair<Int, Int> {
    val step = shown.count { it != current } + 1
    val position = applicable.indexOf(current)
    // An access shown already and missing again after this one (taken back during the activation) is counted once.
    return step to step + missing.count { it !in shown && applicable.indexOf(it) > position }
}

/** [onPass], for a step the others do not need: offered in place of "Later" once the phone refused it. */
@Composable
private fun AccessStep(
    access: Access,
    step: Int,
    steps: Int,
    mayBeRestricted: Boolean,
    onAnswered: () -> Unit,
    onLater: () -> Unit,
    onPass: (() -> Unit)?,
) {
    val context = LocalContext.current
    // Null once all granted: an access still missing with them is fixed in the settings — the app's notifications turned
    // off or down since (review of 2026-11-03, R-04), or the full-screen alerts never allowed (Android 15, installed from
    // a file: measured). Asked again, the phone would answer "granted" without a dialog. Read again after each answer and
    // each return to the front: when the access stays missing, nothing else redraws this step, and its button went on
    // asking for what was granted — a step with no way out (measured on the Android 15 emulator, 2026-11-04).
    var reads by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reads++ }
    val permissions = remember(reads) {
        runtimePermissions(access)?.takeUnless { asked ->
            asked.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }
    }
    val text = accessText(access, phoneAsks = permissions != null)
    // The phone stops showing its dialog once refused twice (or "don't ask again"), and the request then answers
    // "refused" at once: only the settings can grant it from there. The app cannot tell that from a dialog closed with
    // Back, or by an incoming call (Android 11+: also "refused", and not counted): after any refusal it offers both,
    // the dialog again and the settings (general review of 2026-11-02, 4). Saved across a rotation.
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        reads++
        onAnswered()
        if (results.values.any { granted -> !granted }) refused = true
    }
    // The phone asks these with a dialog of its own: "Allow" here, and after a refusal, the settings as well.
    val phoneAsks = text.refused != null

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
                    text = stringResource(R.string.activation_step, step, steps),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(text.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(text.why), style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(text.how), style = MaterialTheme.typography.bodyLarge)
                if (refused) {
                    text.refused?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodyLarge) }
                }
                if (mayBeRestricted) RestrictedSettingsHelp(R.string.restricted_steps)
            }
            Button(
                onClick = {
                    if (permissions != null) {
                        ask.launch(permissions)
                    } else {
                        // A dialog of the settings answers nothing back: back here with the access still missing, it
                        // was refused, or never shown. A step that can be passed is, once tried.
                        if (phoneAsks || onPass != null) refused = true
                        AccessSettings.open(context, settingsPages(access, context))
                    }
                },
                colors = brandButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (phoneAsks) R.string.access_allow else R.string.access_open_settings),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            when {
                phoneAsks && refused -> SecondButton(R.string.access_open_settings) {
                    AccessSettings.open(context, refusedPages(access, context))
                }
                mayBeRestricted -> SecondButton(R.string.restricted_open_app_info) {
                    AccessSettings.open(context, AccessSettings.appInfo(context))
                }
            }
            val pass = onPass?.takeIf { refused }
            TextButton(onClick = pass ?: onLater, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(if (pass != null) R.string.access_skip else R.string.access_later))
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

private fun settingsPages(access: Access, context: Context): List<Intent> = when (access) {
    Access.CALLS -> AccessSettings.appInfo(context)
    Access.SCREEN -> AccessSettings.screen(context)
    Access.BACKGROUND -> AccessSettings.background(context)
    Access.AUTOSTART -> AccessSettings.autostart(context)
    Access.NOTIFICATIONS -> AccessSettings.notifications(context)
    Access.ALERTS -> AccessSettings.alerts(context)
}

/** Once the phone's own dialog was refused: where the same access is granted by hand. */
private fun refusedPages(access: Access, context: Context): List<Intent> =
    if (access == Access.ALERTS) AccessSettings.alerts(context) else AccessSettings.appInfo(context)
