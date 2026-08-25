package com.example.tvbrowser.ui.home

import android.content.Intent
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.UaMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookmarkEditStepsTest {

    class HostActivity : FragmentActivity(), BookmarkDetailsStep.Host {
        override fun stepContainerId(): Int = android.R.id.content
    }

    private lateinit var controller: ActivityController<HostActivity>
    private lateinit var repository: BookmarkRepository

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        repository = BookmarkRepository(AppDatabase.getInstance(ApplicationProvider.getApplicationContext()).bookmarkDao())
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun addStep(step: androidx.fragment.app.Fragment) {
        controller.get().supportFragmentManager.beginTransaction()
            .add(android.R.id.content, step)
            .addToBackStack(null)
            .commit()
        controller.get().supportFragmentManager.executePendingTransactions()
    }

    private fun playbackStep(): BookmarkPlaybackStep? =
        controller.get().supportFragmentManager.fragments.filterIsInstance<BookmarkPlaybackStep>().firstOrNull()

    @Test
    fun blankTitleRejectedAndDialogStaysOpen() {
        val step = BookmarkDetailsStep.newInstance(null)
        addStep(step)

        assertFalse(step.tryContinue("   ", "service.example.tv"))

        assertEquals(
            controller.get().getString(R.string.bookmark_details_error_title),
            step.lastError
        )
        assertNull(playbackStep())
    }

    @Test
    fun oversizedTitleRejected() {
        val step = BookmarkDetailsStep.newInstance(null)
        addStep(step)

        assertFalse(step.tryContinue("x".repeat(BookmarkDetailsStep.MAX_TITLE_LENGTH + 1), "service.example.tv"))

        assertNotNull(step.lastError)
        assertNull(playbackStep())
    }

    @Test
    fun invalidUrlRejectedWithUrlError() {
        val step = BookmarkDetailsStep.newInstance(null)
        addStep(step)

        assertFalse(step.tryContinue("My Service", "https://"))

        assertEquals(
            controller.get().getString(R.string.bookmark_details_error_url),
            step.lastError
        )
        assertNull(playbackStep())
    }

    @Test
    fun validDetailsContinueOpensPlaybackOptions() {
        val step = BookmarkDetailsStep.newInstance(null)
        addStep(step)

        assertTrue(step.tryContinue("My Service", "service.example.tv"))
        idleMain()

        assertNotNull(playbackStep())
    }

    @Test
    fun duplicateOriginStillAllowsContinue() = runBlocking {
        repository.upsert(Bookmark(title = "Existing", url = "https://dup.example/a", origin = "https://dup.example"))
        val step = BookmarkDetailsStep.newInstance(null)
        addStep(step)

        assertTrue(step.tryContinue("Duplicate Entry", "https://dup.example/b"))
        idleMain()

        assertNotNull(playbackStep())
    }

    @Test
    fun playbackSavePersistsServiceWithDefaults() = runBlocking {
        val details = BookmarkDetailsStep.newInstance(null)
        addStep(details)
        assertTrue(details.tryContinue("Saved Service", "https://saved.example/watch"))
        idleMain()

        playbackStep()!!.save(useDefaults = true)

        awaitUntil { repository.findByOrigin("https://saved.example") != null }
        val saved = repository.findByOrigin("https://saved.example")!!
        assertEquals("Saved Service", saved.title)
        assertEquals(UaMode.DESKTOP, saved.uaMode)
        assertEquals(100, saved.textZoomPercent)
    }

    private fun awaitUntil(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!runBlocking { condition() } && System.currentTimeMillis() < deadline) {
            idleMain()
            Thread.sleep(10)
        }
        idleMain()
        org.junit.Assert.assertTrue("condition not met in time", runBlocking { condition() })
    }

    @Test
    fun editFlowPreservesIdentityWhileUpdatingFields() = runBlocking {
        val original = Bookmark(
            title = "Original",
            url = "https://edit.example/old",
            origin = "https://edit.example",
            uaMode = UaMode.MOBILE,
            textZoomPercent = 125,
            sortOrder = 7
        )
        val storedId = repository.upsert(original)

        val step = BookmarkPlaybackStep.newInstance(
            title = "Renamed",
            url = "https://edit.example/new",
            existing = original.copy(id = storedId),
            duplicateWarning = false
        )
        addStep(step)
        step.save(useDefaults = false)

        awaitUntil { repository.findById(storedId)?.title == "Renamed" }

        val updated = repository.findById(storedId)
        assertNotNull(updated)
        assertEquals(storedId, updated!!.id)
        assertEquals("Renamed", updated.title)
        assertEquals("https://edit.example/new", updated.url)
        assertEquals(7, updated.sortOrder)
    }
}
