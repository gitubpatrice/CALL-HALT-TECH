package com.filestech.callhalt.platform.notification

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The validation-code rule, against its corpus (`validation-code-sms.txt`): what it must stop — a missed code is a code
 * read out to a scammer — and what it must let through — a false one is a STOP during an ordinary call. Each line says
 * which, and the file's header says what was decided against the first verdict, and why.
 */
class ValidationCodeTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    fun `each message of the corpus gets its decided verdict`(case: Case) {
        assertWithMessage("${case.id}: ${case.text}").that(isValidationCode(case.text)).isEqualTo(case.isCode)
    }

    /** A corpus read wrong would pass every case: it must hold what it says it holds. */
    @Test
    fun `the corpus is read whole, codes and non-codes`() {
        val cases = corpus()
        assertThat(cases.size).isAtLeast(MIN_CASES)
        assertThat(cases.count { it.isCode }).isAtLeast(MIN_CASES_EACH_WAY)
        assertThat(cases.count { !it.isCode }).isAtLeast(MIN_CASES_EACH_WAY)
    }

    class Case(val id: String, val isCode: Boolean, val text: String) {
        override fun toString() = id
    }

    companion object {
        private const val MIN_CASES = 120
        private const val MIN_CASES_EACH_WAY = 40
        private const val NO_BREAK_SPACE = "\u00A0"
        private const val NARROW_NO_BREAK_SPACE = "\u202F"

        @JvmStatic
        fun corpus(): List<Case> {
            val resource = checkNotNull(ValidationCodeTest::class.java.getResource("/validation-code-sms.txt"))
            return resource.readText().lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val (id, verdict, text) = line.split('|', limit = 3)
                    require(verdict == "C" || verdict == "L") { "$id: verdict $verdict" }
                    val typeset = text.replace("{NBSP}", NO_BREAK_SPACE).replace("{NNBSP}", NARROW_NO_BREAK_SPACE)
                    Case(id, verdict == "C", typeset)
                }
        }
    }
}
