package com.filestech.callhalt.platform.stop

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.filestech.callhalt.domain.model.StopChoice
import com.filestech.callhalt.platform.access.AccessSettings
import com.filestech.callhalt.platform.install.REMOTE_CONTROL_APPS
import com.filestech.callhalt.platform.install.uninstallPages
import com.filestech.callhalt.platform.telephony.CallEvents
import com.filestech.callhalt.ui.stop.StopScreen
import com.filestech.callhalt.ui.theme.CallHaltTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The real STOP screen (docs/ARCHITECTURE.md, D-006): an activity of its own, started from the background over
 * whatever the user is doing — the banking app included, which hides overlay windows but not activities. Its
 * rules live in [StopSession], tested on the JVM; this class wires them to Android.
 *
 * - **Out of screenshots and screen sharing** (`FLAG_SECURE`): a remote-control app streaming the screen sees it
 *   black. From Android 14, **closed to accessibility services that are not accessibility tools**: such an app
 *   can neither read it nor press "Continue" (P-15; TalkBack, an accessibility tool, still reads it).
 * - **Over the lock screen**: during a call the screen sleeps against the cheek; the STOP is there when the user
 *   looks. Sleeping is not leaving. What opens another app (the dialler, the settings) asks for the unlock first.
 * - **Back does not close it**: an answer is a choice. Leaving it otherwise (Home, another app in front) closes it
 *   and frees its triggers, so that a repeat shows a new screen — never one nobody sees.
 * - **It follows the call**: once its call has ended, it says so and no longer offers to hang up.
 * - A second STOP while one is shown takes the screen, with its own countdown; the screen speaks of the graver of the
 *   two ([StopSession.shown]), and the answer counts for both. Once the first is answered (the phone asking for the
 *   unlock), the second is a screen of its own, and nothing opens over it.
 */
@AndroidEntryPoint
class StopActivity : ComponentActivity() {

    @Inject lateinit var display: StopDisplay

    @Inject lateinit var answers: StopAnswers

    @Inject lateinit var calls: CallControl

    @Inject lateinit var callEvents: CallEvents

    @Inject lateinit var notifier: StopNotifier

    private var session by mutableStateOf<StopSession?>(null)

    /** Rebuilt while the phone asked for the unlock: the answer whose request to make again, once in front. */
    private var reopening: StopChoice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.decorView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
        setShowWhenLocked(true)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            // An answer is a choice; Back is none.
        }
        // Rebuilt (rotation, or the process died): the saved session, not the first intent — a later STOP may
        // have replaced it, and the call may have ended since.
        val restored = StopSession.fromBundle(savedInstanceState?.getBundle(KEY_SESSION))
        val created = restored ?: StopRequest.readFrom(intent)?.let(StopSession::of)
        if (created == null) {
            Timber.w("STOP screen started without a request")
            finish()
            return
        }
        session = created
        // Rebuilt while the phone asked for the unlock (unlockThen): Android drops the first screen's request with it
        // (KeyguardManager: no callback once the activity is destroyed). Asked again once this one is in front.
        reopening = created.opening
        // Created: Android let the start through (StopDisplay), or the notification opened it — which it replaces.
        display.shown(created.current.id)
        notifier.cancel(created.current.id)
        // For the screen's whole life, not only while in front: an end missed while it was hidden would leave it
        // offering to cut the next call.
        lifecycleScope.launch {
            callEvents.endedCalls.collect { channel -> session = session?.afterCallEnded(channel) }
        }
        setContent {
            CallHaltTheme {
                session?.let { current ->
                    // A new request is a new screen: its own countdown. The call ending changes the words and the
                    // buttons, not the countdown.
                    key(current.current.id) {
                        val permitted = remember { calls.canEndCall() }
                        StopScreen(
                            trigger = current.shown,
                            onCall = current.current.onCall,
                            channel = current.current.channel,
                            canEndCall = permitted && !current.callStillOn,
                            demo = false,
                            onAnswer = ::answer,
                            installedApp = current.uninstallable?.let(REMOTE_CONTROL_APPS::get),
                        )
                    }
                }
            }
        }
        // A new screen, not one rebuilt (a rotation, or the process died): felt as well as seen — last, once all the
        // rest is in place.
        if (restored == null) buzzForStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        StopRequest.readFrom(intent)?.let { next ->
            // Arrived while the phone asks for the unlock: a screen of its own, answered by its own buttons.
            session = session?.receiving(next) ?: StopSession.of(next)
            display.shown(next.id)
            notifier.cancel(next.id)
            buzzForStop()
        }
    }

    override fun onResume() {
        super.onResume()
        if (calls.noCallGoingOn()) session = session?.afterNoCallLeft()
        val current = session
        if (current?.answered == true && current.opening == null) {
            // Rebuilt as it closed: its answer is given, nothing left to open. Checked here, not in onCreate: a STOP
            // that arrived meanwhile is delivered before onResume (onNewIntent), and it stays.
            finish()
        } else {
            // A request must come from a screen in front: the rebuilt screen's, now — unless a new STOP took it.
            val pending = reopening
            reopening = null
            if (pending != null && current?.opening == pending) unlockThen(pending)
        }
    }

    override fun onStop() {
        super.onStop()
        val current = session ?: return
        // Left, not slept: the screen still on, no rotation, no answer given. Hidden for good — out of the recent
        // apps — it would keep its triggers "shown" and absorb the repeats.
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!isChangingConfigurations && !current.answered && interactive) {
            session = current.answering()
            answers.left(current)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        session?.let { outState.putBundle(KEY_SESSION, it.toBundle()) }
    }

    private fun answer(choice: StopChoice) {
        // The first answer only: finish() is not immediate, and a second tap must not end another call.
        val current = session?.takeUnless { it.answered } ?: return
        if (answers.answer(current, choice)) {
            if (choice in OPENS) {
                session = current.answering(opening = choice)
                unlockThen(choice)
            } else {
                session = current.answering()
                finish()
            }
        } else {
            // The call could not be ended: the screen stays, without its call buttons; the advice says to hang up.
            session = current.afterCallNotEnded()
        }
    }

    /**
     * The dialler and the settings do not show over the lock screen: opened from this screen on a locked phone, they
     * wait behind it — the STOP gone, the lock screen back, nothing said (measured on Android 16, 2026-11-07). The
     * phone asks for the unlock first, over this screen, then [open] — the call already ended, without waiting for it.
     * Refused or failed, [open] all the same: the page waits behind the lock screen, as before, and the answer stands.
     */
    private fun unlockThen(choice: StopChoice) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            open(choice)
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open(choice)

                override fun onDismissCancelled() = open(choice)

                override fun onDismissError() = open(choice)
            },
        )
    }

    /**
     * Opens what [choice] opens, once, and closes the screen — unless a new STOP took it while the phone asked for the
     * unlock ([StopSession.receiving]): that one stays, to be answered.
     */
    private fun open(choice: StopChoice) {
        val current = session?.takeIf { it.opening == choice } ?: return
        session = current.opened()
        when (choice) {
            StopChoice.CALL_MY_BANK -> openDialler()
            StopChoice.REMOVE_ACCESS -> openOwnSettings(AccessSettings.accessibility())
            StopChoice.UNINSTALL -> current.uninstallable?.let { openOwnSettings(uninstallPages(it)) }
            else -> Unit
        }
        finish()
    }

    /**
     * The phone's dialler, empty: the bank's number comes with the settings (PROJET.md, § 4.5). In a task of its own,
     * as all this screen opens: in this screen's, it would stay under the next STOP and come back after its "Continue"
     * (measured with the settings page, Android 16, 2026-11-07).
     */
    private fun openDialler() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (missing: ActivityNotFoundException) {
            Timber.w(missing, "No dialler")
        }
    }

    /**
     * A page of the phone's settings: the accessibility page, where the user turns the app off (D-033), or the page of the
     * remote-control app just installed, where "Uninstall" is (FN-3, D-039) — as the phone's OWN settings serve it: the
     * app just given the access, or just installed, could declare the same page ([AccessSettings.openOwn]). In a task of
     * its own, as the dialler, and that task cleared: the settings the user was just in (where the access was given)
     * would otherwise keep their own page in front.
     */
    private fun openOwnSettings(pages: List<Intent>) {
        val cleared = pages.map { page -> page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        if (!AccessSettings.openOwn(this, cleared)) Timber.w("No system settings for %s", pages.first().action)
    }

    private companion object {
        const val KEY_SESSION = "com.filestech.callhalt.stop.SESSION"

        /** The answers that open something once the phone is unlocked. */
        val OPENS = setOf(StopChoice.CALL_MY_BANK, StopChoice.REMOVE_ACCESS, StopChoice.UNINSTALL)
    }
}
