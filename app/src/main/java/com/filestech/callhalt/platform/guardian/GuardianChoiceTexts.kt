package com.filestech.callhalt.platform.guardian

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.filestech.callhalt.R
import com.filestech.callhalt.data.guardian.Guardian
import com.filestech.callhalt.data.guardian.guardianNumber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * The two texts the user's own choice sends (Patrice, 2026-11-07): to the relative just chosen — the number is right,
 * texting works, and they know they were chosen — and to the one no longer, so that "remove your daughter's number"
 * asked on the phone does not go unnoticed.
 */
interface ChoiceTexts {
    fun setUp(): String

    fun removed(): String
}

class ResourceChoiceTexts @Inject constructor(@ApplicationContext private val context: Context) : ChoiceTexts {
    override fun setUp(): String = context.getString(R.string.guardian_sms_set_up)

    override fun removed(): String = context.getString(R.string.guardian_sms_removed)
}

/** The contact the user picked, as a relative: null when it has no number the app may text. */
fun interface ContactReader {
    suspend fun read(picked: Uri): Guardian?
}

/**
 * Read through the one-time grant the contacts app gives with its answer: no permission to read the contacts. Only the
 * phone's own contacts — any app may answer the pick, and hand back a number of its choosing from a provider of its own.
 */
class PhoneContacts @Inject constructor(@ApplicationContext private val context: Context) : ContactReader {

    // A catch-all, by design: a maker's contacts provider may throw anything on an answer it did not expect, and a crash
    // here would close the relative's screen (review of 2026-11-07, 4). A blocking query: no cancellation to swallow.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(picked: Uri): Guardian? = withContext(Dispatchers.IO) {
        if (picked.authority != ContactsContract.AUTHORITY) {
            Timber.w("Picked contact not from the phone's contacts")
            return@withContext null
        }
        try {
            context.contentResolver.query(picked, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME), null, null, null)?.use { row ->
                if (!row.moveToFirst()) return@use null
                guardianNumber(row.getString(0).orEmpty())?.let { Guardian(it, row.getString(1)) }
            }
        } catch (unreadable: RuntimeException) {
            Timber.w(unreadable, "Picked contact unreadable")
            null
        }
    }
}
