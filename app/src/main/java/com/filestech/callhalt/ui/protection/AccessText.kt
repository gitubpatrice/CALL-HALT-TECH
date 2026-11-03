package com.filestech.callhalt.ui.protection

import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.platform.access.Access

/**
 * The words of one access: its name on the home screen, why it is asked, and what to do on the phone's page. [refused]
 * is for an access the phone asks with a dialog of its own: what to do once it was refused, in the settings.
 */
internal data class AccessText(
    @StringRes val title: Int,
    @StringRes val why: Int,
    @StringRes val how: Int,
    @StringRes val refused: Int? = null,
)

/**
 * Exhaustive on purpose: a new access does not compile until it says why it is asked and how it is granted. [phoneAsks]:
 * the phone's dialog will show — false for [Access.ALERTS] before Android 13, or once granted and the notifications
 * turned off or down since, or the full-screen ones: then only the settings can (review of 2026-11-03, R-03, R-04).
 */
internal fun accessText(access: Access, phoneAsks: Boolean = true): AccessText = when (access) {
    Access.CALLS -> AccessText(
        R.string.access_calls_title,
        R.string.access_calls_why,
        R.string.access_calls_how,
        R.string.access_calls_refused,
    )
    Access.SCREEN -> AccessText(R.string.access_screen_title, R.string.access_screen_why, R.string.access_screen_how)
    Access.BACKGROUND -> AccessText(
        R.string.access_background_title,
        R.string.access_background_why,
        R.string.access_background_how,
        R.string.access_background_refused,
    )
    Access.NOTIFICATIONS ->
        AccessText(R.string.access_notifications_title, R.string.access_notifications_why, R.string.access_notifications_how)
    Access.ALERTS -> if (phoneAsks) {
        AccessText(R.string.access_alerts_title, R.string.access_alerts_why, R.string.access_alerts_how, R.string.access_alerts_refused)
    } else {
        AccessText(R.string.access_alerts_title, R.string.access_alerts_why, R.string.access_alerts_how_settings)
    }
}
