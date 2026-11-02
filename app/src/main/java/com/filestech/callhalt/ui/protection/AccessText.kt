package com.filestech.callhalt.ui.protection

import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.platform.access.Access

/** The words of one access: its name on the home screen, why it is asked, and what to do on the phone's page. */
internal data class AccessText(
    @StringRes val title: Int,
    @StringRes val why: Int,
    @StringRes val how: Int,
)

/** Exhaustive on purpose: a new access does not compile until it says why it is asked and how it is granted. */
internal fun accessText(access: Access): AccessText = when (access) {
    Access.CALLS -> AccessText(R.string.access_calls_title, R.string.access_calls_why, R.string.access_calls_how)
    Access.SCREEN -> AccessText(R.string.access_screen_title, R.string.access_screen_why, R.string.access_screen_how)
    Access.NOTIFICATIONS ->
        AccessText(R.string.access_notifications_title, R.string.access_notifications_why, R.string.access_notifications_how)
}
