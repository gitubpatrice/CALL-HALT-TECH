package com.filestech.callhalt.platform.guardian

import android.content.Context
import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.data.guardian.GuardianRepository
import com.filestech.callhalt.di.ApplicationScope
import com.filestech.callhalt.domain.model.GuardianEvent
import com.filestech.callhalt.domain.model.Moment
import com.filestech.callhalt.platform.PhoneClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toKotlinDuration

/** Tells the relative what the engine decided to tell them (Decision.NotifyGuardian; docs/ARCHITECTURE.md, D-034). */
fun interface GuardianNotifier {
    suspend fun tell(event: GuardianEvent)
}

/** The relative set up, read when an alert is to be sent: the one of this moment, not of the engine's decision. */
fun interface GuardianSource {
    suspend fun current(): Guardian?
}

/** The words of a text, in the phone's language. */
internal fun interface GuardianWords {
    fun of(text: GuardianText): String
}

/**
 * By SMS ([TextSender]), at most what [GuardianLimiter] allows. A text is counted once the phone took it: one it refused
 * (the permission taken back) holds the next alert back for nothing, and one that then fails (no network) is no longer
 * counted. Nothing is waited for: the STOP decided with it is already on the screen.
 */
@Singleton
class SmsGuardianNotifier @Inject internal constructor(
    private val guardians: GuardianSource,
    private val sender: TextSender,
    private val clock: PhoneClock,
    private val words: GuardianWords,
    @ApplicationScope private val scope: CoroutineScope,
) : GuardianNotifier {

    private val limiter = GuardianLimiter()

    override suspend fun tell(event: GuardianEvent) {
        val text = guardianText(event)
        val guardian = text?.let { guardians.current() }
        val at = clock.now()
        when {
            text == null -> Timber.i("No text to the relative for %s yet", event::class.simpleName)
            // The engine decided with a relative set up: removed since, there is no one to tell.
            guardian == null -> Timber.i("No relative to text")
            !limiter.allows(event, at) -> Timber.i("Relative already told of this alert")
            else -> send(event, at, guardian.number, words.of(text))
        }
    }

    private fun send(event: GuardianEvent, at: Moment, to: String, text: String) {
        val sent = sender.send(to, text) ?: return Timber.w("Text to the relative not handed to the phone")
        limiter.record(event, at)
        scope.launch {
            val outcome = withTimeoutOrNull(RESULT_WAIT) { sent.await() }
            Timber.i("Text to the relative: %s", if (outcome == true) "sent" else "NOT sent ($outcome)")
            // Failed, it holds the next one back for nothing; with no answer yet, the phone may still send it.
            if (outcome == false) limiter.release(event, at)
        }
    }

    private companion object {
        /**
         * As long as the text holds the next one back: a failure the phone reports later still frees its place (GPT
         * review of 2026-11-07, 2); after it, there is no place left to free. No answer by then: logged as not sent.
         */
        val RESULT_WAIT = GuardianLimiter.HOLD.toKotlinDuration()
    }
}

/** [GuardianRepository], seen as the alerts need it. */
class RepositoryGuardianSource @Inject constructor(private val repository: GuardianRepository) : GuardianSource {
    override suspend fun current(): Guardian? = repository.current()
}

/** The app's resources, in the phone's language of the moment. */
internal class ContextGuardianWords @Inject constructor(@ApplicationContext private val context: Context) : GuardianWords {
    override fun of(text: GuardianText): String = text.compose(context)
}
