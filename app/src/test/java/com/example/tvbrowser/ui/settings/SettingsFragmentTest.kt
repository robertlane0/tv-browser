package com.example.tvbrowser.ui.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Looper
import androidx.preference.Preference
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.UaMode
import kotlinx.coroutines.flow.first
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsFragmentTest {

    private lateinit var controller: ActivityController<SettingsActivity>
    private lateinit var repository: BookmarkRepository
    private lateinit var sessionBookmark: Bookmark

    @Before
    fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = BookmarkRepository(AppDatabase.getInstance(context).bookmarkDao())
        sessionBookmark = Bookmark(
            title = "Session Service",
            url = "https://session.example/watch",
            origin = "https://session.example",
            uaMode = UaMode.DESKTOP,
            textZoomPercent = 100
        )
        val id = repository.upsert(sessionBookmark)
        sessionBookmark = sessionBookmark.copy(id = id)

        val intent = Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_BOOKMARK, sessionBookmark)
        controller = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun fragment(): SettingsFragment =
        controller.get().supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().first()

    @Test
    fun globalDefaultUaPickPersistsToDataStore() = runBlocking {
        assertTrue(fragment().handleRadioPicked(SettingsFragment.KEY_DEFAULT_UA, "MOBILE"))

        awaitUntil { preferencesRepository().globalUaDefault().first() == UaMode.MOBILE }
    }

    @Test
    fun nativeTvNotSelectableInReleaseForGlobalDefault() {
        controller.get().applicationInfo.flags = 0
        val fm = controller.get().supportFragmentManager
        fm.beginTransaction()
            .add(android.R.id.content, SettingsFragment.newInstance(Intent()))
            .commitNow()
        val releaseFragment = fm.fragments.filterIsInstance<SettingsFragment>().last()

        assertFalse(releaseFragment.handleRadioPicked(SettingsFragment.KEY_DEFAULT_UA, "NATIVE_TV"))
        assertTrue(releaseFragment.handleRadioPicked(SettingsFragment.KEY_DEFAULT_UA, "DESKTOP"))
        assertTrue(fragment().findPreference<Preference>(SettingsFragment.KEY_DEFAULT_UA)!!.isVisible)
    }

    @Test
    fun sessionUaChangeShowsConfirmationAndReloadsOnlyOnConfirm() = runBlocking {
        assertTrue(fragment().handleRadioPicked(SettingsFragment.KEY_SESSION_UA, "MOBILE"))

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("confirmation must be shown", dialog)

        assertFalse("no reload before confirm", controller.get().isFinishing)
        dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(controller.get().isFinishing)
        val resultIntent = shadowOf(controller.get()).getResultIntent()
        assertNotNull(resultIntent)
        assertTrue(resultIntent!!.getBooleanExtra(SettingsActivity.RESULT_RELOAD_REQUESTED, false))

        awaitUntil { repository.findById(sessionBookmark.id)?.uaMode == UaMode.MOBILE }
        assertEquals(UaMode.MOBILE, repository.findById(sessionBookmark.id)!!.uaMode)
    }

    @Test
    fun decliningReloadConfirmationKeepsSettingsOpenWithoutResult() {
        fragment().handleRadioPicked(SettingsFragment.KEY_SESSION_UA, "MOBILE")

        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(controller.get().isFinishing)
        assertNull(shadowOf(controller.get()).getResultIntent())
    }

    @Test
    fun textZoomChangePersistsAndConfirmsBeforeReload() = runBlocking {
        assertTrue(fragment().handleRadioPicked(SettingsFragment.KEY_TEXT_ZOOM, "150"))

        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(controller.get().isFinishing)
        awaitUntil { repository.findById(sessionBookmark.id)?.textZoomPercent == 150 }
    }

    @Test
    fun zoomRowHiddenWithoutSessionBookmark() {
        val fresh = Robolectric.buildActivity(
            SettingsActivity::class.java,
            Intent(ApplicationProvider.getApplicationContext(), SettingsActivity::class.java)
        ).setup()
        try {
            val f = fresh.get().supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().first()
            assertFalse(f.findPreference<Preference>(SettingsFragment.KEY_TEXT_ZOOM)!!.isVisible)
            assertFalse(f.findPreference<Preference>(SettingsFragment.KEY_SESSION_UA)!!.isVisible)
            assertTrue(f.findPreference<Preference>(SettingsFragment.KEY_DEFAULT_UA)!!.isVisible)
        } finally {
            fresh.pause().stop().destroy()
        }
    }

    @Test
    fun clearSessionRemovesCookiesAndStorageButKeepsBookmarks() = runBlocking {
        android.webkit.CookieManager.getInstance().setCookie("https://keepout.example", "sid=secret")
        assertNotNull(android.webkit.CookieManager.getInstance().getCookie("https://keepout.example"))
        val bookmarksBefore = repository.observeAll().first()

        val pref = fragment().findPreference<Preference>("clear_session_data")!!
        pref.onPreferenceClickListener!!.onPreferenceClick(pref)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("clearing must require confirmation", dialog)
        dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(android.webkit.CookieManager.getInstance().getCookie("https://keepout.example"))
        val bookmarksAfter = repository.observeAll().first()
        assertEquals(bookmarksBefore.size, bookmarksAfter.size)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun engineAndAboutRowsPopulated() {
        val engine = fragment().findPreference<Preference>("browser_engine")!!
        val about = fragment().findPreference<Preference>("about")!!

        assertEquals(
            controller.get().getString(R.string.settings_engine),
            engine.title.toString()
        )
        assertTrue(about.summary?.isNotEmpty() == true)
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

    private fun preferencesRepository(): com.example.tvbrowser.data.PreferencesRepository =
        com.example.tvbrowser.data.PreferencesRepository.getInstance(
            ApplicationProvider.getApplicationContext()
        )
}
