package com.filestech.callhalt.ui.protection

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.callhalt.R

/**
 * The way through the phone's "restricted settings" (measured, docs/ARCHITECTURE.md, P-3 and D-034): the menu that
 * allows them shows in the app info only once the phone has refused once. [steps] say how, from the screen showing it:
 * the activation's access, or the relative's permission to send texts. The button that opens the app info stays with the
 * screen's other buttons.
 */
@Composable
internal fun RestrictedSettingsHelp(@StringRes steps: Int) {
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
            Text(text = stringResource(steps), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
