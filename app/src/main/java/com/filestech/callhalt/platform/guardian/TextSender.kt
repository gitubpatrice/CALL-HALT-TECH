package com.filestech.callhalt.platform.guardian

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Hands texts to the phone, which sends them from the user's own number (docs/ARCHITECTURE.md, D-034). */
interface TextSender {

    /** SEND_SMS granted: without it, nothing can be sent. */
    fun canSend(): Boolean

    /**
     * Hands [text] to the phone for [to]; null when it could not take it (the permission refused, a phone with no SMS).
     * The result comes later: true once every part went out, false at the first that failed — no network, no SIM,
     * airplane mode. Never completed if the process dies first.
     */
    fun send(to: String, text: String): Deferred<Boolean>?
}

/**
 * Android's `SmsManager`, the default SIM's: a phone with two SIM cards and no default one for texts is not measured
 * (Patrice: "plus tard"). The app is not the SMS app: Android itself files each text it sends in the conversation with
 * that number, where the user sees it.
 */
@Singleton
class SmsTextSender @Inject constructor(@ApplicationContext private val context: Context) : TextSender {

    private val pending = ConcurrentHashMap<Int, Pending>()

    /** From anywhere: a late answer to a text of an earlier process must not be taken for one of this process's. */
    private val ids = AtomicInteger(Random.nextInt(ID_SPAN))

    override fun canSend(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    // A catch-all, by design: whatever the phone throws (no SMS on a tablet, a refusal) is a text not sent, never a
    // failure of the STOP decided with it.
    @Suppress("TooGenericExceptionCaught")
    override fun send(to: String, text: String): Deferred<Boolean>? {
        if (!canSend()) return null
        val id = ids.incrementAndGet()
        return try {
            val manager = smsManager()
            val parts = manager.divideMessage(text)
            val outcome = CompletableDeferred<Boolean>()
            pending[id] = Pending(parts.size, outcome)
            // One result per part, on the same intent: it says which text, the result code says how it went.
            val sent = sentIntent(id)
            manager.sendMultipartTextMessage(to, null, ArrayList(parts), ArrayList(List(parts.size) { sent }), null)
            outcome
        } catch (failure: RuntimeException) {
            pending.remove(id)
            Timber.w(failure, "Text not handed to the phone")
            null
        }
    }

    /** One part of text [id] went out, or not ([SmsSentReceiver]). */
    internal fun onPartSent(id: Int, ok: Boolean) {
        // A part after one that failed, or a text of an earlier process.
        val text = pending[id] ?: return Timber.i("Text %d: no longer awaited", id)
        val done = synchronized(text) {
            text.sent++
            !ok || text.sent == text.parts
        }
        if (done) {
            pending.remove(id)
            text.outcome.complete(ok)
        }
    }

    @Suppress("DEPRECATION") // getDefault: the only way before Android 12; the same default SIM.
    private fun smsManager(): SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checkNotNull(context.getSystemService(SmsManager::class.java)) { "No SMS on this phone" }
    } else {
        SmsManager.getDefault()
    }

    /** Explicit, to this app's own receiver, which no other app can reach (not exported). */
    private fun sentIntent(id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, SmsSentReceiver::class.java).putExtra(SmsSentReceiver.EXTRA_TEXT, id),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private class Pending(val parts: Int, val outcome: CompletableDeferred<Boolean>) {
        var sent = 0
    }

    private companion object {
        /** Ids from a random start below this: far from the maximum, which they never reach. */
        const val ID_SPAN = 1 shl 30
    }
}

/**
 * The phone's answer for each part of a text ([SmsTextSender]): not exported — the phone sends it as this app, through
 * the app's own PendingIntent. The result code is the only thing read (an immutable intent takes no extras from it).
 */
@AndroidEntryPoint
class SmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var sender: SmsTextSender

    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == Activity.RESULT_OK
        if (!ok) Timber.w("Text part not sent: code %d", resultCode)
        sender.onPartSent(intent.getIntExtra(EXTRA_TEXT, NONE), ok)
    }

    internal companion object {
        const val EXTRA_TEXT = "com.filestech.callhalt.extra.TEXT"
        private const val NONE = -1
    }
}
