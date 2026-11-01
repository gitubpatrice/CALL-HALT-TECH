package com.filestech.callhalt.domain.model

import java.time.Duration

/**
 * What the user set (PROJET.md, § 4). The defaults are the PROTECTIVE ones: a fresh install protects
 * before anyone opens the settings. A value outside its range is a bug of the caller, never stored: the
 * settings store falls back to the default instead (it must fail on the protective side, not crash).
 */
data class ProtectionSettings(
    val enabled: Boolean = true,
    val afterCallWindow: Duration = DEFAULT_AFTER_CALL_WINDOW,
    val longCallThreshold: Duration = DEFAULT_LONG_CALL_THRESHOLD,
    /** Text the relative on STOP, on "Continue" and on long calls. Off until a relative is set up. */
    val guardianAlerts: Boolean = false,
) {
    init {
        require(afterCallWindow in AFTER_CALL_WINDOW_RANGE) { "afterCallWindow out of range: $afterCallWindow" }
        require(longCallThreshold in LONG_CALL_THRESHOLD_RANGE) { "longCallThreshold out of range: $longCallThreshold" }
    }

    companion object {
        val DEFAULT_AFTER_CALL_WINDOW: Duration = Duration.ofMinutes(15)
        val AFTER_CALL_WINDOW_RANGE: ClosedRange<Duration> = Duration.ZERO..Duration.ofMinutes(60)

        val DEFAULT_LONG_CALL_THRESHOLD: Duration = Duration.ofMinutes(10)
        val LONG_CALL_THRESHOLD_RANGE: ClosedRange<Duration> = Duration.ofMinutes(1)..Duration.ofMinutes(120)
    }
}
