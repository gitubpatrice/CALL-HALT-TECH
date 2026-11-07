package com.filestech.callhalt.ui.guardian

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.callhalt.R
import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.platform.access.AccessSettings
import com.filestech.callhalt.ui.protection.RestrictedSettingsHelp
import com.filestech.callhalt.ui.theme.brandButtonColors
import timber.log.Timber
import java.util.Locale

/**
 * The relative (docs/ARCHITECTURE.md, D-034; PROJET.md, § 4.7): who is texted when a STOP shows or "Continue" is chosen
 * despite it. A contact picked, or a number typed; the permission to send texts asked here, at the moment it is needed
 * — not by the activation: no relative, no text. What the last move did is said in words, read out by TalkBack.
 */
@Composable
fun GuardianScreen(onDone: () -> Unit, viewModel: GuardianViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val move = viewModel::on
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { move(GuardianMove.Resumed) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        move(GuardianMove.PermissionAnswered)
    }
    val pick = rememberLauncherForActivityResult(PickPhoneNumber(), viewModel::picked)
    val step = state.step
    LaunchedEffect(step) {
        if (step is GuardianStep.AskPermission && !step.asked) {
            move(GuardianMove.PermissionAsked)
            askPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.guardian_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.guardian_why), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.guardian_how), style = MaterialTheme.typography.bodyLarge)
            // The phone's question names the whole SMS group, reading included: said before it is asked (Patrice).
            if (!state.canSend) {
                Text(text = stringResource(R.string.guardian_permission_note), style = MaterialTheme.typography.bodyLarge)
            }
            state.guardian?.let { guardian ->
                CurrentGuardian(guardian, state.canSend) { askPermission.launch(Manifest.permission.SEND_SMS) }
            }
            StepMessage(step, viewModel.textsMayBeRestricted, move)
            ChooseGuardian(
                busy = step is GuardianStep.Sending,
                onPick = {
                    try {
                        pick.launch(Unit)
                    } catch (missing: ActivityNotFoundException) {
                        Timber.w(missing, "No contacts app")
                        move(GuardianMove.NoContactsApp)
                    }
                },
                onTyped = { move(GuardianMove.Typed(it)) },
            )
            if (state.guardian != null) WideOutlinedButton(R.string.guardian_remove) { move(GuardianMove.AskRemove) }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.guardian_done))
            }
        }
    }
    val removing = state.guardian
    if (step == GuardianStep.ConfirmRemove && removing != null) RemoveDialog(removing, move)
}

/** Confirmed in the app's own words: "Stop alerting" names what the touch does. */
@Composable
private fun RemoveDialog(removing: Guardian, move: (GuardianMove) -> Unit) {
    AlertDialog(
        onDismissRequest = { move(GuardianMove.Cancel) },
        text = { Text(text = stringResource(R.string.guardian_remove_confirm, guardianLabel(removing))) },
        confirmButton = {
            TextButton(onClick = { move(GuardianMove.Remove) }) {
                Text(text = stringResource(R.string.guardian_remove_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { move(GuardianMove.Cancel) }) {
                Text(text = stringResource(R.string.guardian_cancel))
            }
        },
    )
}

/** The relative set up; without the permission, said, with the way to give it back. */
@Composable
private fun CurrentGuardian(guardian: Guardian, canSend: Boolean, onAllow: () -> Unit) {
    val label = guardianLabel(guardian)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.guardian_current, label), style = MaterialTheme.typography.titleMedium)
            if (canSend) {
                Text(text = stringResource(R.string.guardian_change_note, label), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = stringResource(R.string.home_guardian_cannot_send),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                WideButton(R.string.guardian_allow, onClick = onAllow)
            }
        }
    }
}

/** What the user's last move did, in words; nothing while there is nothing to say. */
@Composable
private fun StepMessage(step: GuardianStep, restricted: Boolean, move: (GuardianMove) -> Unit) {
    when (step) {
        GuardianStep.InvalidNumber -> Said(stringResource(R.string.guardian_number_invalid), error = true)
        GuardianStep.NoContactsApp -> Said(stringResource(R.string.guardian_no_contacts))
        is GuardianStep.PermissionRefused -> Refused(restricted, move)
        is GuardianStep.Sending -> Said(stringResource(R.string.guardian_sending, guardianLabel(step.to)))
        is GuardianStep.Sent -> Said(stringResource(R.string.guardian_sent, guardianLabel(step.to)))
        is GuardianStep.NotSent -> {
            Said(stringResource(R.string.guardian_not_sent, guardianLabel(step.to)), error = true)
            WideButton(R.string.guardian_resend) { move(GuardianMove.Resend) }
        }
        GuardianStep.Removed -> Said(stringResource(R.string.guardian_removed))
        GuardianStep.SaveFailed -> Said(stringResource(R.string.guardian_save_failed), error = true)
        GuardianStep.Idle, GuardianStep.ConfirmRemove, is GuardianStep.AskPermission -> Unit
    }
}

/**
 * The permission refused. Installed from a file, Android 15 and 16 refuse it without a question, until the restricted
 * settings are allowed (measured, D-034): the way through them. Elsewhere, refused twice, the phone stops asking: the
 * way to it in the settings.
 */
@Composable
private fun Refused(restricted: Boolean, move: (GuardianMove) -> Unit) {
    val context = LocalContext.current
    Said(stringResource(R.string.guardian_sms_refused), error = true)
    if (restricted) {
        RestrictedSettingsHelp(R.string.guardian_restricted_steps)
    } else {
        Text(text = stringResource(R.string.guardian_sms_settings_steps), style = MaterialTheme.typography.bodyLarge)
    }
    WideButton(R.string.guardian_allow) { move(GuardianMove.AskAgain) }
    WideOutlinedButton(if (restricted) R.string.restricted_open_app_info else R.string.access_open_settings) {
        AccessSettings.open(context, AccessSettings.appInfo(context))
    }
}

/** Read out by TalkBack when it changes: the answer to a button touched a moment ago. */
@Composable
private fun Said(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * A number typed is validated with the keyboard closed: left open, it hid what the app answered — sent, or not a number
 * it may text (measured on the Android 15 emulator).
 */
@Composable
private fun ChooseGuardian(busy: Boolean, onPick: () -> Unit, onTyped: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canSave = !busy && typed.isNotBlank()
    val save = {
        focus.clearFocus()
        onTyped(typed)
    }
    WideButton(R.string.guardian_pick, enabled = !busy, onClick = onPick)
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(text = stringResource(R.string.guardian_number_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
        modifier = Modifier.fillMaxWidth(),
    )
    WideOutlinedButton(R.string.guardian_save, enabled = canSave, onClick = save)
}

@Composable
private fun WideButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = brandButtonColors(), modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(label), modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun WideOutlinedButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(label))
    }
}

/** "Julie (06 12 34 56 78)", or the number alone when typed: as the phone writes numbers in this country. */
@Composable
internal fun guardianLabel(guardian: Guardian): String {
    val number = remember(guardian.number) {
        PhoneNumberUtils.formatNumber(guardian.number, Locale.getDefault().country) ?: guardian.number
    }
    return guardian.name?.let { stringResource(R.string.guardian_contact, it, number) } ?: number
}

/**
 * The phone's contacts, one number picked: the app reads that one through the grant the contacts app gives with it —
 * no permission to read the contacts ([com.filestech.callhalt.platform.guardian.PhoneContacts]).
 */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}
