package com.filestech.callhalt.platform.stop

import com.filestech.callhalt.domain.model.SensitiveActionKind

/**
 * The trigger a STOP — screen or notification — speaks of when it stands for several: the gravest, the newest among
 * equals (publication audit, N-1). [kinds] in their order of arrival, the newest last; null when there is none.
 */
internal fun gravest(kinds: Collection<SensitiveActionKind>): SensitiveActionKind? {
    val top = kinds.maxOfOrNull(::gravity) ?: return null
    return kinds.last { gravity(it) == top }
}

/** [kinds] in their order of arrival, each kept at its LAST arrival: a trigger coming again is the newest. */
internal fun inArrivalOrder(kinds: Collection<SensitiveActionKind>): List<SensitiveActionKind> =
    kinds.toList().asReversed().distinct().asReversed()

/**
 * How much a trigger's own words matter on the screen. Exhaustive on purpose: a new trigger does not compile until it
 * says where it stands.
 */
internal fun gravity(kind: SensitiveActionKind): Int = when (kind) {
    // Its way out is in the phone's settings, and switching off does not end it (D-033).
    SensitiveActionKind.ACCESSIBILITY_ENABLED -> 2
    // Someone may be seeing or controlling the phone: hanging up does not stop that (D-017).
    SensitiveActionKind.REMOTE_CONTROL_OPENED, SensitiveActionKind.REMOTE_CONTROL_INSTALLED -> 1
    // Hang up, and call the bank: the same advice for each.
    SensitiveActionKind.CODE_MESSAGE,
    SensitiveActionKind.SMS_BURST,
    SensitiveActionKind.BANK_VALIDATION_REQUEST,
    SensitiveActionKind.BANK_APP_OPENED,
    -> 0
}
