package com.filestech.callhalt.platform.telephony

import com.filestech.callhalt.platform.FailingPreferences
import com.filestech.callhalt.platform.InMemoryPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * The memory's logic, on [InMemoryPreferences]. Its survival on a real file, across processes, is proven on
 * Android (`PhoneStateMemoryOnDeviceTest`).
 */
class PhoneStateMemoryTest {

    @Test
    fun `each state returns the one it replaces, nothing before the first`() = runTest {
        val memory = PhoneStateMemory(InMemoryPreferences())
        assertThat(memory.swap(PhoneState.RINGING)).isNull()
        assertThat(memory.swap(PhoneState.OFFHOOK)).isEqualTo(PhoneState.RINGING)
        assertThat(memory.swap(PhoneState.IDLE)).isEqualTo(PhoneState.OFFHOOK)
    }

    /** A memory that cannot be read must not cost the call: it is still reported, its direction unknown. */
    @Test
    fun `a storage failure of any kind answers unknown instead of throwing`() = runTest {
        assertThat(PhoneStateMemory(FailingPreferences(IOException("disk unavailable"))).swap(PhoneState.OFFHOOK)).isNull()
        assertThat(PhoneStateMemory(FailingPreferences(IllegalStateException("two stores on one file"))).swap(PhoneState.OFFHOOK))
            .isNull()
    }
}
