package com.filestech.callhalt.platform

import com.filestech.callhalt.domain.model.Moment

/** A phone's clocks set by the test: [now] as it says, the process started [processStarted]. */
class FakePhoneClock(var now: Moment, var processStarted: Moment = now) : PhoneClock {
    override fun now(): Moment = now

    override fun processStarted(): Moment = processStarted
}
