package com.filestech.callhalt.platform.stop

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
 *   looks. Sleeping is not leaving.
 * - **Back does not close it**: an answer is a choice. Leaving it otherwise (Home, another app in front) closes it
 *   and frees its triggers, so that a repeat shows a new screen — never one nobody sees.
 * - **It follows the call**: once its call has ended, it says so and no longer offers to hang up.
 * - A second STOP while one is shown takes the screen, with its own countdown; the answer counts for both.
 */
@AndroidEntryPoint
class StopActivity : ComponentActivity() {

    @Inject lateinit var display: StopDisplay

    @Inject lateinit var answers: StopAnswers

    @Inject lateinit var calls: CallControl

    @Inject lateinit var callEvents: CallEvents

    @Inject lateinit var notifier: StopNotifier

    private var session by mutableStateOf<StopSession?>(null)

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
        // Created: Android let the start through (StopDisplay), or the notification opened it — which it replaces.
        display.shown(created.current.id)
        notifier.cancel()
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
                            trigger = current.current.trigger,
                            onCall = current.current.onCall,
                            channel = current.current.channel,
                            canEndCall = permitted && !current.callStillOn,
                            demo = false,
                            onAnswer = ::answer,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        StopRequest.readFrom(intent)?.let { next ->
            session = session?.replacedBy(next) ?: StopSession.of(next)
            display.shown(next.id)
            notifier.cancel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (calls.noCallGoingOn()) session = session?.afterNoCallLeft()
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
            session = current.answering()
            if (choice == StopChoice.CALL_MY_BANK) openDialler()
            finish()
        } else {
            // The call could not be ended: the screen stays, without its call buttons; the advice says to hang up.
            session = current.afterCallNotEnded()
        }
    }

    /** The phone's dialler, empty: the bank's number comes with the settings (PROJET.md, § 4.5). */
    private fun openDialler() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL))
        } catch (missing: ActivityNotFoundException) {
            Timber.w(missing, "No dialler")
        }
    }

    private companion object {
        const val KEY_SESSION = "com.filestech.callhalt.stop.SESSION"
    }
}
