package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.domain.model.CallChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The channels on which the phone has just reported NO call — the end of one, or an idle phone — told to whoever
 * listens in this process: the STOP screen, so that it stops saying "you are on the phone" and offering to hang up
 * once its call is over. Cheap on purpose: the screen injects it on the main thread, unlike the sensor, which
 * reaches the database.
 */
@Singleton
class CallEvents @Inject constructor() {

    private val ended = MutableSharedFlow<CallChannel>(extraBufferCapacity = BUFFER)

    val endedCalls: SharedFlow<CallChannel> = ended.asSharedFlow()

    fun callEnded(channel: CallChannel) {
        ended.tryEmit(channel)
    }

    private companion object {
        /** Ends come seconds apart; a listener that falls this far behind has a stale screen anyway. */
        const val BUFFER = 8
    }
}
