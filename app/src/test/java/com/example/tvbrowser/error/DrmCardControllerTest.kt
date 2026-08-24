package com.example.tvbrowser.error

import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DrmCardControllerTest {

    private lateinit var activity: androidx.fragment.app.FragmentActivity
    private lateinit var card: com.example.tvbrowser.error.DrmCardView
    private lateinit var controller: DrmCardController
    private var refocusCount = 0

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java).setup().get()
        card = com.example.tvbrowser.error.DrmCardView(activity)
        (activity.findViewById(android.R.id.content) as FrameLayout).addView(card)
        refocusCount = 0
        controller = DrmCardController(card) { refocusCount++ }
    }

    private fun keyEvent(action: Int, keyCode: Int): KeyEvent =
        KeyEvent(0L, 0L, action, keyCode, 0)

    @Test
    fun showRevealsCardAndMovesFocusToIt() {
        assertFalse(card.isFocused)

        controller.show()

        assertTrue(controller.isVisible())
        assertTrue(card.isFocusable)
        assertTrue("card must hold focus while visible", card.isFocused)
        assertEquals(0, refocusCount)
    }

    @Test
    fun backKeyDownDismissesCardAndReturnsFocusImmediately() {
        controller.show()

        val consumed = card.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        assertTrue("DOWN must be consumed before activity back handling", consumed)
        assertFalse(controller.isVisible())
        assertEquals(1, refocusCount)
    }

    @Test
    fun backKeyUpAfterDismissFallsThroughHarmlessly() {
        controller.show()
        card.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        val consumed = card.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertFalse("hidden card must not intercept stray key-ups", consumed)
        assertEquals(1, refocusCount)
    }

    @Test
    fun centerKeyDismissesOnDownForRemoteUsers() {
        controller.show()

        val consumed = card.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))

        assertTrue(consumed)
        assertFalse(controller.isVisible())
        assertEquals(1, refocusCount)
    }

    @Test
    fun unrelatedKeysDoNotDismiss() {
        controller.show()

        card.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))

        assertTrue(controller.isVisible())
    }

    @Test
    fun directDismissHidesAndRefocuses() {
        controller.show()

        controller.dismiss()

        assertFalse(controller.isVisible())
        assertEquals(1, refocusCount)
    }
}
