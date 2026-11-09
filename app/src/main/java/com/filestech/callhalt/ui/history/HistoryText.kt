package com.filestech.callhalt.ui.history

import android.content.res.Resources
import androidx.annotation.StringRes
import com.filestech.callhalt.R
import com.filestech.callhalt.domain.history.Alert
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.domain.model.WarningReason
import com.filestech.callhalt.platform.guardian.what
import com.filestech.callhalt.platform.install.REMOTE_CONTROL_APPS
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle

/**
 * The words of the history (D-041), one function for the screen and for the text shared, so that the bank or the police
 * read what the user saw: for each alert, when, what it was, the app it came from, and what was chosen.
 */
internal class HistoryText(private val resources: Resources) {

    /**
     * The day and the time, in the phone's zone and the app's language, with that zone's offset: "9 nov. 2026, 10:44
     * (UTC+01:00)" — read by a bank elsewhere, after a journey, or in the hour the clocks go back, which comes twice
     * (review of 2026-11-09, 2).
     */
    private val dates = DateTimeFormatterBuilder()
        .appendLocalized(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .appendLiteral(" (UTC")
        .appendOffset("+HH:MM", "")
        .appendLiteral(")")
        .toFormatter(resources.configuration.locales[0])

    /**
     * The alert's lines, its time first — in the phone's zone as it is now, read at each call: a zone changed while the
     * screen is open is not part of its configuration, and the text shared must not keep the old one (GPT review of
     * 2026-11-09, 2).
     */
    fun lines(alert: Alert): List<String> = buildList {
        add(dates.format(alert.at.atZone(ZoneId.systemDefault())))
        when (alert) {
            is Alert.Stop -> {
                add(resources.getString(R.string.history_stop, resources.getString(what(alert.trigger))))
                alert.source?.let { add(resources.getString(R.string.history_app, appName(it))) }
                if (alert.notShown) add(resources.getString(R.string.history_not_shown))
                if (alert.answers.isNotEmpty()) {
                    val then = resources.getString(R.string.history_then)
                    add(resources.getString(R.string.history_answer, alert.answers.joinToString(then) { resources.getString(words(it)) }))
                } else if (!alert.notShown) {
                    add(resources.getString(R.string.history_no_answer))
                }
            }
            is Alert.Warning -> add(resources.getString(R.string.history_warning, warning(alert)))
        }
    }

    /** The whole history as one text, for the share menu: a heading, then each alert, the newest first. */
    fun shared(alerts: List<Alert>): String =
        (listOf(resources.getString(R.string.history_share_heading)) + alerts.map { lines(it).joinToString("\n") })
            .joinToString("\n\n")

    private fun warning(alert: Alert.Warning): String = when (alert.reason) {
        // Its own words, not the notification's "has just been installed": read months later, or by the bank (i18n
        // pass of 2026-11-09, 5).
        WarningReason.REMOTE_CONTROL_INSTALLED ->
            resources.getString(R.string.history_remote_installed, alert.source?.let(::appName).orEmpty())
        WarningReason.LONG_CALL_WITH_UNKNOWN -> resources.getString(R.string.history_long_call)
    }

    /** A remote-control app by the name of our list, as its STOP and its warning say; any other by its package name. */
    private fun appName(packageName: String): String = REMOTE_CONTROL_APPS[packageName] ?: packageName

    /** Exhaustive on purpose: a new answer does not compile until the history says it. */
    @StringRes
    private fun words(choice: StopChoice): Int = when (choice) {
        StopChoice.HANG_UP -> R.string.stop_hang_up
        StopChoice.CALL_MY_BANK -> R.string.stop_call_my_bank
        StopChoice.REMOVE_ACCESS -> R.string.stop_remove_access
        StopChoice.UNINSTALL -> R.string.history_answer_uninstall
        StopChoice.CONTINUE -> R.string.stop_continue
        StopChoice.LEFT -> R.string.history_answer_left
    }
}
