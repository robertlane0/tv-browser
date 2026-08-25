package com.example.tvbrowser.ui.browser

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tvbrowser.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AddressEntryStepsTest {

    class HostFragment : Fragment(), AddressInputStep.Host, DpadKeyGridStep.Host {

        val committedUrls = mutableListOf<String>()
        var cancelCount = 0

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val root = FrameLayout(requireContext())
            root.id = R.id.overlay_step_container
            return root
        }

        override fun onAddressCommitted(normalizedUrl: String) {
            committedUrls.add(normalizedUrl)
        }

        override fun onAddressEntryCancelled() {
            cancelCount++
        }

        fun currentModalStep(): Fragment? =
            childFragmentManager.fragments.firstOrNull()
    }

    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var host: HostFragment

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(android.R.id.content, HostFragment())
            .commitNow()
        host = controller.get().supportFragmentManager.fragments.filterIsInstance<HostFragment>().first()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun addStep(step: Fragment) {
        host.childFragmentManager.beginTransaction()
            .add(R.id.overlay_step_container, step)
            .addToBackStack(null)
            .commit()
        host.childFragmentManager.executePendingTransactions()
    }

    @Test
    fun invalidAddressKeepsDialogOpenWithInlineError() {
        val step = AddressInputStep.newInstance("https://service.example.tv")
        addStep(step)

        assertFalse(step.handleAddressInput("https://"))

        assertTrue("invalid input must keep the dialog open", host.currentModalStep() === step)
        assertTrue(host.committedUrls.isEmpty())
    }

    @Test
    fun emptyAddressRejectedWithoutNavigation() {
        val step = AddressInputStep.newInstance("")
        addStep(step)

        assertFalse(step.handleAddressInput("   "))

        assertTrue(host.committedUrls.isEmpty())
        assertTrue(host.currentModalStep() === step)
    }

    @Test
    fun bareHostCommitNormalizesToHttpsAndNotifiesHost() {
        val step = AddressInputStep.newInstance("")
        addStep(step)

        assertTrue(step.handleAddressInput("service.example.tv/watch"))

        assertEquals(listOf("https://service.example.tv/watch"), host.committedUrls)
        idleMain()
        assertTrue("committed entry must close the dialog", host.currentModalStep() == null)
    }

    @Test
    fun httpsUrlPassesThroughUnmodified() {
        val step = AddressInputStep.newInstance("")
        addStep(step)

        assertTrue(step.handleAddressInput("https://secure.example/play"))

        assertEquals(listOf("https://secure.example/play"), host.committedUrls)
    }

    @Test
    fun keyGridDraftPreviewIsSingleLine() {
        val step = DpadKeyGridStep.newInstance("https://service.example.tv/some/long/path")
        addStep(step)

        val draft = step.view!!.findViewById<android.widget.TextView>(R.id.key_grid_draft)
        assertTrue("long drafts must not wrap the grid off-screen", draft.isSingleLine)
    }

    @Test
    fun keyGridBuildsDraftAndRejectsEmptyCommit() {
        val step = DpadKeyGridStep.newInstance("")
        addStep(step)

        listOf('o', 'p', 'e', 'n').forEach { step.append(it) }
        assertEquals("open", step.currentDraft)

        step.deleteLast()
        step.deleteLast()
        step.deleteLast()
        step.deleteLast()
        step.clearAll()
        step.commit()

        assertTrue("grid must stay open after invalid commit", step.errorShown())
        assertTrue(host.committedUrls.isEmpty())
        assertTrue(host.currentModalStep() === step)
    }

    @Test
    fun keyGridDeleteClearAndValidCommit() {
        val step = DpadKeyGridStep.newInstance("https://a.tv/x")
        addStep(step)

        step.deleteLast()
        assertEquals("https://a.tv/", step.currentDraft)

        step.clearAll()
        assertEquals("", step.currentDraft)

        "example.tv".forEach { step.append(it) }
        step.commit()

        assertEquals(listOf("https://example.tv"), host.committedUrls)
    }

    @Test
    fun cancelledGridReportsCancelWhileAddressStepStaysOpen() {
        val address = AddressInputStep.newInstance("https://service.example.tv")
        addStep(address)
        val grid = DpadKeyGridStep.newInstance("")
        addStep(grid)

        host.childFragmentManager.popBackStack()
        idleMain()

        assertEquals(1, host.cancelCount)
        assertTrue("address dialog must remain underneath", host.currentModalStep() === address)
    }
}
