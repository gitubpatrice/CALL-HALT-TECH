package com.filestech.callhalt.domain.model

/**
 * How a call reaches the phone. A VOIP call (WhatsApp, Signal, Telegram…) is invisible to the telephony
 * stack: it is tracked only if an adapter can see it (docs/ARCHITECTURE.md, open point P-4).
 */
enum class CallChannel { CELLULAR, VOIP }

enum class CallDirection { INCOMING, OUTGOING, UNKNOWN }

/**
 * Who is at the other end, as far as the phone can tell — never a proof: a number is spoofed in seconds,
 * a saved contact's included (PROJET.md, § 3). The STOP rule never reads it; the long-call reminder does.
 */
sealed interface Counterpart {
    /** The caller withheld the number. */
    data object Hidden : Counterpart

    /** The phone could not tell (permission missing, VOIP app). */
    data object Unreadable : Counterpart

    data class Number(val raw: String, val inContacts: Boolean) : Counterpart {
        init {
            require(raw.isNotBlank()) { "A blank number is Unreadable, not a Number." }
        }
    }
}

/** One call, on one channel. [endedAt] is null while the call is in progress. */
data class CallSession(
    val channel: CallChannel,
    val direction: CallDirection,
    val counterpart: Counterpart,
    val startedAt: Moment,
    val endedAt: Moment? = null,
) {
    init {
        require(endedAt == null || !endedAt.isBefore(startedAt)) { "A call cannot end before it starts." }
    }

    val isActive: Boolean get() = endedAt == null

    /** Someone this phone does not know: the condition of the long-call reminder (PROJET.md, § 4.2). */
    val isWithUnknown: Boolean
        get() = when (counterpart) {
            Counterpart.Hidden -> true
            is Counterpart.Number -> !counterpart.inContacts
            // Not knowing is not "unknown": an hour on WhatsApp with a grandson must not alert anyone.
            Counterpart.Unreadable -> false
        }
}
