package com.filestech.callhalt.data.guardian

/**
 * The relative the app texts (docs/ARCHITECTURE.md, D-034): a [number] it may text ([guardianNumber]), and the [name]
 * the user knows them by when picked among the contacts — for this app's own screens, never sent. Kept on the phone.
 */
data class Guardian(val number: String, val name: String? = null)

/**
 * [typed] as a number the app may text, or null: spaces, dots, dashes, slashes and brackets dropped, a leading "+" kept,
 * any other sign refused — a letter, a pause, "*" or "#" is no number one texts. From [MIN_DIGITS] digits: a short
 * number is a service, and some charge for each text they receive (in France, the 5-digit "SMS+" numbers); someone on
 * the phone could have one entered as the relative, and each STOP would then cost money. Up to [MAX_DIGITS], the
 * longest number there is (E.164).
 *
 * As numbers come (review of 2026-11-07, faux positifs 2 and 4): "+49 (0)171…", the national 0 that the international
 * form leaves out, written in brackets as contacts often keep it — dropped, or the number reaches no one; a number copied
 * with invisible marks around it, or written with typographic dashes. A mark that would SHOW the digits in another order
 * than the one kept is refused: the user would read one number and text another.
 */
internal fun guardianNumber(typed: String): String? {
    if (typed.any { it in REORDERING }) return null
    // The brackets last: "+33-(0)6…" and "0033.(0)6…" have their "(0)" after the country code once the dots and dashes
    // are gone, not before (review of 2026-11-07, security delta, 1).
    val compact = typed.filterNot { it.isWhitespace() || it in INVISIBLE || it in SEPARATORS }
    val kept = compact.replaceFirst(TRUNK_ZERO, "$1").filterNot { it in BRACKETS }
    val plus = kept.startsWith('+')
    val digits = if (plus) kept.drop(1) else kept
    val valid = digits.length in MIN_DIGITS..MAX_DIGITS && digits.all { it in '0'..'9' }
    return when {
        !valid -> null
        plus -> "+$digits"
        else -> digits
    }
}

/**
 * Whether two numbers the app may text reach the same person, written two ways: "06 12 34 56 78" and "+33 6 12 34 56
 * 78" — by their last [SUBSCRIBER_DIGITS] digits, a French mobile's own, which no country code nor leading 0 changes;
 * by fewer for a shorter number (Monaco, French Polynesia: 8). A relative chosen again in another form is not told they
 * no longer will be alerted (review of 2026-11-07, 3; faux positifs 7).
 */
internal fun sameSubscriber(first: String, second: String): Boolean {
    val firstDigits = first.filter(Char::isDigit)
    val secondDigits = second.filter(Char::isDigit)
    val compared = minOf(SUBSCRIBER_DIGITS, firstDigits.length, secondDigits.length)
    return firstDigits.takeLast(compared) == secondDigits.takeLast(compared)
}

private const val SUBSCRIBER_DIGITS = 9
private const val MIN_DIGITS = 8
private const val MAX_DIGITS = 15

/** "(0)" right after a country code — "+" or "00", then 1 to 3 digits — and only there (GPT review of 2026-11-07, 6). */
private val TRUNK_ZERO = Regex("""^((?:\+|00)\d{1,3})\(0\)""")

private const val BRACKETS = "()"

/** Dots, slashes, and every dash a keyboard or a contacts app writes. */
private const val SEPARATORS = ".-/\u2010\u2011\u2012\u2013\u2212"

/** Marks that show nothing and move nothing: zero-width spaces and joiners, left-to-right marks and isolates. */
private const val INVISIBLE = "\u200B\u200C\u200D\u200E\u2060\uFEFF\u202A\u202C\u202D\u2066\u2069"

/** Marks that can show digits right to left, or in an order of their own. */
private const val REORDERING = "\u200F\u202B\u202E\u2067\u2068"
