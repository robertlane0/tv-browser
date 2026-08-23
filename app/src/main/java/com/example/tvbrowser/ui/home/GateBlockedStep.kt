package com.example.tvbrowser.ui.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.tvbrowser.R

class GateBlockedStep : GuidedStepSupportFragment() {

    interface Host {
        fun onGateRetry()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.gate_blocked_title),
            getString(R.string.gate_blocked_description),
            null,
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_OPEN_SETTINGS)
                .title(getString(R.string.gate_action_open_settings))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_RETRY)
                .title(getString(R.string.gate_action_retry))
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_OPEN_SETTINGS -> {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                }
            }
            ACTION_RETRY -> {
                (activity as? Host)?.onGateRetry()
            }
        }
    }

    companion object {
        private const val ACTION_OPEN_SETTINGS = 1L
        private const val ACTION_RETRY = 2L
    }
}
