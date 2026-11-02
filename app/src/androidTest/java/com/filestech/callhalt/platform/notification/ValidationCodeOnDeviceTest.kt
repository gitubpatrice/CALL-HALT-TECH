package com.filestech.callhalt.platform.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The validation-code corpus (`validation-code-sms.txt`, shared with ValidationCodeTest), on Android's own regex engine:
 * it reads `\b`, case and Unicode its own way (ICU), and the JVM of the unit tests does not decide what the phone does.
 */
@RunWith(AndroidJUnit4::class)
class ValidationCodeOnDeviceTest {

    @Test
    fun everyMessageOfTheCorpusGetsItsDecidedVerdictOnThePhone() {
        val resource = checkNotNull(javaClass.getResource("/validation-code-sms.txt"))
        val cases = resource.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val wrong = cases.mapNotNull { line ->
            val (id, verdict, text) = line.split('|', limit = 3)
            val typeset = text.replace("{NBSP}", "\u00A0").replace("{NNBSP}", "\u202F")
            id.takeIf { isValidationCode(typeset) != (verdict == "C") }
        }
        assertThat(cases.size).isAtLeast(MIN_CASES)
        assertThat(wrong).isEmpty()
    }

    private companion object {
        const val MIN_CASES = 120
    }
}
