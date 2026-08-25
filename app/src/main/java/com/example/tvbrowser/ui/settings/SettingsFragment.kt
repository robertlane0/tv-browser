package com.example.tvbrowser.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.webkit.WebViewCompat
import com.example.tvbrowser.R
import com.example.tvbrowser.data.AppDatabase
import com.example.tvbrowser.data.Bookmark
import com.example.tvbrowser.data.BookmarkRepository
import com.example.tvbrowser.data.PreferencesRepository
import com.example.tvbrowser.data.UaMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : LeanbackPreferenceFragmentCompat() {

    private lateinit var preferences: PreferencesRepository

    @Suppress("DEPRECATION")
    private var sessionBookmark: Bookmark? = null

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferencesRepository.getInstance(requireContext())
        @Suppress("DEPRECATION")
        sessionBookmark = requireActivity().intent.getParcelableExtra(SettingsActivity.EXTRA_BOOKMARK)
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        writeScope.cancel()
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey)

        configureDefaultUa()
        configureSessionUa()
        configureTextZoom()
        configureCleanupSwitch()
        configureClearSession()
        configureEngineRow()
        configureAboutRow()
    }

    private fun configureDefaultUa() {
        val pref = findPreference<ListPreference>(KEY_DEFAULT_UA) ?: return
        applyUaOptions(pref)
        lifecycleScope.launch {
            pref.value = preferences.globalUaDefault().first().name
        }
        pref.setOnPreferenceChangeListener { _, newValue ->
            val mode = runCatching { UaMode.valueOf(newValue.toString()) }.getOrNull() ?: return@setOnPreferenceChangeListener false
            if (mode == UaMode.NATIVE_TV && !isDebuggable()) return@setOnPreferenceChangeListener false
            writeScope.launch { preferences.setGlobalUaDefault(mode) }
            true
        }
    }

    private fun configureSessionUa() {
        val pref = findPreference<ListPreference>(KEY_SESSION_UA) ?: return
        val bookmark = sessionBookmark
        if (bookmark == null) {
            pref.isVisible = false
            return
        }
        applyUaOptions(pref)
        pref.value = bookmark.uaMode.name
        pref.setOnPreferenceChangeListener { _, newValue ->
            val mode = runCatching { UaMode.valueOf(newValue.toString()) }.getOrNull() ?: return@setOnPreferenceChangeListener false
            persistSessionBookmark(bookmark.copy(uaMode = mode))
            showReloadConfirmation()
            false
        }
    }

    private fun configureTextZoom() {
        val pref = findPreference<ListPreference>(KEY_TEXT_ZOOM) ?: return
        val bookmark = sessionBookmark
        if (bookmark == null) {
            pref.isVisible = false
            return
        }
        pref.value = bookmark.textZoomPercent.toString()
        pref.setOnPreferenceChangeListener { _, newValue ->
            val zoom = newValue.toString().toIntOrNull() ?: return@setOnPreferenceChangeListener false
            persistSessionBookmark(bookmark.copy(textZoomPercent = zoom))
            showReloadConfirmation()
            false
        }
    }

    private fun configureCleanupSwitch() {
        val pref = findPreference<SwitchPreference>(KEY_CONTENT_FILTER) ?: return
        lifecycleScope.launch {
            pref.isChecked = preferences.contentFilterEnabled().first()
        }
        pref.setOnPreferenceChangeListener { _, newValue ->
            writeScope.launch { preferences.setContentFilterEnabled(newValue as Boolean) }
            true
        }
    }

    private fun configureClearSession() {
        val pref = findPreference<Preference>(KEY_CLEAR_SESSION) ?: return
        pref.setOnPreferenceClickListener {
            showClearSessionConfirmation()
            true
        }
    }

    private fun configureEngineRow() {
        val pref = findPreference<Preference>(KEY_BROWSER_ENGINE) ?: return
        val provider = runCatching { WebViewCompat.getCurrentWebViewPackage(requireContext()) }.getOrNull()
        pref.title = getString(R.string.settings_engine)
        pref.summary = provider?.let { "${it.packageName}  v${it.versionName}" }
            ?: getString(R.string.settings_engine_unknown)
    }

    private fun configureAboutRow() {
        val pref = findPreference<Preference>(KEY_ABOUT) ?: return
        val version = runCatching {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull()
        pref.summary = "${getString(R.string.app_name)}  v${version ?: "?"}"
    }

    private fun applyUaOptions(pref: ListPreference) {
        if (isDebuggable()) {
            val labels = requireContext().resources.getStringArray(R.array.settings_default_ua_labels)
                .toMutableList()
            val values = requireContext().resources.getStringArray(R.array.settings_default_ua_values)
                .toMutableList()
            labels.add(getString(R.string.ua_mode_native_tv_debug))
            values.add(UaMode.NATIVE_TV.name)
            pref.entries = labels.toTypedArray()
            pref.entryValues = values.toTypedArray()
        } else {
            pref.entries =
                requireContext().resources.getStringArray(R.array.settings_default_ua_labels)
            pref.entryValues =
                requireContext().resources.getStringArray(R.array.settings_default_ua_values)
        }
    }

    private fun persistSessionBookmark(updated: Bookmark) {
        sessionBookmark = updated
        val appContext = requireContext().applicationContext
        writeScope.launch {
            BookmarkRepository(AppDatabase.getInstance(appContext).bookmarkDao())
                .upsert(updated)
        }
    }

    private fun showReloadConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_reload_confirm_title)
            .setMessage(R.string.settings_reload_confirm_message)
            .setPositiveButton(R.string.settings_reload) { _, _ -> deliverReloadResult() }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showClearSessionConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_clear_session_confirm_title)
            .setMessage(R.string.settings_clear_session_confirm_message)
            .setPositiveButton(R.string.settings_confirm) { _, _ -> clearSessionData() }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    @SuppressLint("ApplySharedPref")
    private fun clearSessionData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(requireContext(), R.string.settings_toast_cleared, Toast.LENGTH_SHORT).show()
        if (sessionBookmark != null) deliverReloadResult()
    }

    private fun deliverReloadResult() {
        activity?.setResult(
            android.app.Activity.RESULT_OK,
            Intent().putExtra(SettingsActivity.RESULT_RELOAD_REQUESTED, true)
        )
        activity?.finish()
    }

    private fun isDebuggable(): Boolean =
        requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val KEY_DEFAULT_UA = "default_ua"
        private const val KEY_SESSION_UA = "session_ua"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        private const val KEY_CONTENT_FILTER = "content_filter_enabled"
        private const val KEY_CLEAR_SESSION = "clear_session_data"
        private const val KEY_BROWSER_ENGINE = "browser_engine"
        private const val KEY_ABOUT = "about"

        fun newInstance(intent: Intent): SettingsFragment =
            SettingsFragment().apply { arguments = intent.extras ?: Bundle() }
    }
}
