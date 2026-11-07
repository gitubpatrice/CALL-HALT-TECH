package com.filestech.callhalt.data.guardian

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** The relative's number: as people write it, or as the contacts keep it — never a short service number. */
class GuardianNumberTest {

    @ParameterizedTest
    @CsvSource(
        "06 12 34 56 78, 0612345678",
        "06.12.34.56.78, 0612345678",
        "06-12-34-56-78, 0612345678",
        "+33 6 12 34 56 78, +33612345678",
        "0033 6 12 34 56 78, 0033612345678",
        "' 0612345678 ', 0612345678",
        // 8 digits, the fewest kept: numbers that short are national numbers in some countries (Monaco's fixed lines).
        "97 98 12 34, 97981234",
        "+49 1512 3456789, +4915123456789",
        "0470/12.34.56, 0470123456",
    )
    fun `a number as people write it is the number`(typed: String, number: String) {
        assertThat(guardianNumber(typed)).isEqualTo(number)
    }

    /** The national 0 in brackets, as contacts keep it after a country code: kept, the number would reach no one. */
    @ParameterizedTest
    @CsvSource(
        "+33 (0)6 12 34 56 78, +33612345678",
        "+49 (0)171 2345678, +491712345678",
        "+41 (0)79 123 45 67, +41791234567",
        "+44 (0)7700 900123, +447700900123",
        "0044 (0)7700 900123, 00447700900123",
        "+44 ( 0 ) 7700 900123, +447700900123",
    )
    fun `the national 0 in brackets after a country code is left out`(typed: String, number: String) {
        assertThat(guardianNumber(typed)).isEqualTo(number)
    }

    /** Elsewhere, a 0 in brackets is a digit of the number: kept (GPT review, 6). */
    @ParameterizedTest
    @CsvSource("+44 20 7123 (0)456, +442071230456", "06 12 (0)3 45 67, 0612034567")
    fun `a 0 in brackets anywhere else is a digit`(typed: String, number: String) {
        assertThat(guardianNumber(typed)).isEqualTo(number)
    }

    /** The contacts keep a non-breaking space between groups on some phones, a narrow one on others; or a tab. */
    @Test
    fun `a number with the spaces of the contacts is the number`() {
        listOf(0x00A0, 0x202F, 0x2007, 0x0009).forEach { space ->
            val typed = listOf("06", "12", "34", "56", "78").joinToString(mark(space))
            assertThat(guardianNumber(typed)).isEqualTo("0612345678")
        }
    }

    /** Copied from a message (invisible marks around it), or written with typographic dashes: the number shown. */
    @Test
    fun `invisible marks and typographic dashes leave the number shown`() {
        val isolated = "${mark(0x202A)}+33 6 12 34 56 78${mark(0x202C)}"
        val withMarks = "${mark(0x2066)}${mark(0xFEFF)}06${mark(0x200B)}12 34 56 78${mark(0x2069)}"
        val withDashes = listOf(0x2010, 0x2011, 0x2013, 0x2212).map { dash -> "06${mark(dash)}12${mark(dash)}34 56 78" }

        assertThat(guardianNumber(isolated)).isEqualTo("+33612345678")
        assertThat(guardianNumber(withMarks)).isEqualTo("0612345678")
        withDashes.forEach { typed -> assertThat(guardianNumber(typed)).isEqualTo("0612345678") }
    }

    /** A mark that would show the digits right to left: the user would read one number, and the app text another. */
    @Test
    fun `a mark that may show the digits in another order is refused`() {
        listOf(0x200F, 0x202B, 0x202E, 0x2067, 0x2068).forEach { reorder ->
            assertThat(guardianNumber("${mark(reorder)}0612345678")).isNull()
        }
    }

    /**
     * A short number is a service, and some charge for every text received (France: "SMS+", 5 digits): someone on the
     * phone could have one entered, and each STOP would cost money. Nor what is no number one texts.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "", " ", "+", "36111", "81212", "3179", "112", "1234567", "795123",
            "06 12 34 56 7a", "*#06#", "0612345678,1234", "0612345678p12", "++33612345678", "33+612345678",
            "+33 6 12 34 56 78 90 12 34", "1234567890123456", "(0)36111",
        ],
    )
    fun `what the app may not text is no number`(typed: String) {
        assertThat(guardianNumber(typed)).isNull()
    }

    /** Chosen again in another form, the same relative: no "you will no longer be alerted". */
    @ParameterizedTest
    @CsvSource(
        "0612345678, +33612345678",
        "0612345678, 0033612345678",
        "+33612345678, +330612345678",
        // 8 digits: Monaco, French Polynesia.
        "97981234, +37797981234",
        "87123456, +68987123456",
    )
    fun `one number written two ways reaches the same person`(first: String, second: String) {
        assertThat(sameSubscriber(first, second)).isTrue()
    }

    @ParameterizedTest
    @CsvSource("0612345678, 0612345679", "+33612345678, +33712345678", "0612345678, 15555215556", "97981234, 97981235")
    fun `two numbers reach two people`(first: String, second: String) {
        assertThat(sameSubscriber(first, second)).isFalse()
    }

    private fun mark(code: Int): String = Char(code).toString()
}
