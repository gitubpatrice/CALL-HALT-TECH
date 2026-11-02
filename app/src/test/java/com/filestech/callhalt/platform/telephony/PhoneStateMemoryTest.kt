package com.filestech.callhalt.platform.telephony

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        assertThat(PhoneStateMemory(FailingStore(IOException("disk unavailable"))).swap(PhoneState.OFFHOOK)).isNull()
        assertThat(PhoneStateMemory(FailingStore(IllegalStateException("two stores on one file"))).swap(PhoneState.OFFHOOK))
            .isNull()
    }

    private class FailingStore(private val failure: Exception) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw failure
    }
}
