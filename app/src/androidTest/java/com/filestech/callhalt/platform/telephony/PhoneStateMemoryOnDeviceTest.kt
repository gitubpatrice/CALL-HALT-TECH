package com.filestech.callhalt.platform.telephony

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On a real file, on Android: Android may kill the app between the ringing and the answer, and the next process
 * must still know the phone rang — or every answered call reads as one of unknown direction.
 */
@RunWith(AndroidJUnit4::class)
class PhoneStateMemoryOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.preferencesDataStoreFile("phone_state_test")

    @After
    fun deleteFile() {
        file.delete()
    }

    /**
     * A second DataStore on the same file, once the first one is gone — what the next process does. Joined, not
     * only cancelled: DataStore refuses two live instances on one file.
     */
    @Test
    fun theLastStateSurvivesANewProcess() = runBlocking {
        val firstProcess = SupervisorJob()
        assertThat(memory(CoroutineScope(firstProcess + Dispatchers.IO)).swap(PhoneState.RINGING)).isNull()
        firstProcess.cancelAndJoin()

        val nextProcess = SupervisorJob()
        try {
            assertThat(memory(CoroutineScope(nextProcess + Dispatchers.IO)).swap(PhoneState.OFFHOOK)).isEqualTo(PhoneState.RINGING)
        } finally {
            nextProcess.cancelAndJoin()
        }
    }

    private fun memory(scope: CoroutineScope) =
        PhoneStateMemory(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
}
