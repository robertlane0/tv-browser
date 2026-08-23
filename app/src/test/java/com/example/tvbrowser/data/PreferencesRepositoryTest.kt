package com.example.tvbrowser.data

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PreferencesRepositoryTest {

    private val fileCounter = AtomicInteger(0)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var repository: PreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(context.filesDir, "test-${fileCounter.incrementAndGet()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = PreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun specDefaultsAreExposed() = runBlocking {
        assertEquals(UaMode.DESKTOP, repository.globalUaDefault().first())
        assertEquals(false, repository.contentFilterEnabled().first())
        assertEquals(0, repository.webviewVersionWarnedMajor().first())
        assertEquals(false, repository.cleartextNoticeShown().first())
    }

    @Test
    fun settersPersistValues() = runBlocking {
        repository.setGlobalUaDefault(UaMode.MOBILE)
        repository.setContentFilterEnabled(true)
        repository.setWebviewVersionWarnedMajor(126)
        repository.setCleartextNoticeShown(true)

        assertEquals(UaMode.MOBILE, repository.globalUaDefault().first())
        assertEquals(true, repository.contentFilterEnabled().first())
        assertEquals(126, repository.webviewVersionWarnedMajor().first())
        assertEquals(true, repository.cleartextNoticeShown().first())
    }
}
