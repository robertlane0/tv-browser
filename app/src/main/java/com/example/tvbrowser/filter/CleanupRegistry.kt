package com.example.tvbrowser.filter

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned selector registry backing the optional cleanup layer (spec 10 §4).
 *
 * Source: `assets/cleanup_registry.json`. Parse failures disable the feature
 * and are logged — they never crash navigation (spec 10 §6).
 */
data class CleanupRegistry(
    val version: Int,
    val generic: GenericSelectors,
    val siteEntries: List<SiteEntry>
) {

    data class GenericSelectors(
        val hideSelectors: List<String> = emptyList(),
        val closeButtonSelectors: List<String> = emptyList()
    )

    data class SiteEntry(
        val origin: String,
        val hideSelectors: List<String> = emptyList(),
        val closeButtonSelectors: List<String> = emptyList(),
        val notes: String? = null
    )

    /** Selectors applicable to [origin]: generic + matching site entry (exact origin match). */
    fun selectorsFor(origin: String): List<String> {
        val genericSelectors = generic.hideSelectors + generic.closeButtonSelectors
        val site = siteEntries.find { it.origin == origin }
        return if (site != null) {
            genericSelectors + site.hideSelectors + site.closeButtonSelectors
        } else {
            genericSelectors
        }
    }

    fun allSelectors(): List<String> =
        generic.hideSelectors + generic.closeButtonSelectors +
            siteEntries.flatMap { it.hideSelectors + it.closeButtonSelectors }

    fun sites(): List<SiteEntry> = siteEntries

    companion object {
        private const val TAG = "CleanupRegistry"

        /** Load from Android assets via [context]. Never throws — corrupt JSON yields empty registry. */
        fun loadFromAssets(context: Context, assetName: String): CleanupRegistry {
            return try {
                val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
                parse(json)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $assetName; cleanup disabled", e)
                empty()
            }
        }

        /**
         * Test convenience: load from the instrumentation/test application assets.
         * Mirrors the spec 10 §7 snippet `CleanupRegistry.loadFromAssets("cleanup_registry.json")`.
         * Uses reflection to avoid a hard test dependency in the main source set.
         */
        fun loadFromAssets(assetName: String): CleanupRegistry {
            return try {
                val clazz = Class.forName("androidx.test.core.app.ApplicationProvider")
                val method = clazz.getMethod("getApplicationContext")
                val context = method.invoke(null) as Context
                loadFromAssets(context, assetName)
            } catch (e: Exception) {
                // Fallback to classloader for pure JVM contexts without ApplicationProvider.
                try {
                    val stream = Thread.currentThread().contextClassLoader
                        ?.getResourceAsStream("assets/$assetName")
                        ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(assetName)
                    if (stream != null) {
                        val json = stream.bufferedReader().use { it.readText() }
                        return parse(json)
                    }
                } catch (ignored: Exception) {}
                Log.w(TAG, "Failed to load $assetName via fallback; cleanup disabled", e)
                empty()
            }
        }

        fun parse(jsonText: String): CleanupRegistry {
            return try {
                val root = JSONObject(jsonText)
                val version = root.optInt("version", 0)
                val genericObj = root.optJSONObject("generic")
                val generic = if (genericObj != null) {
                    GenericSelectors(
                        hideSelectors = jsonArrayToStrings(genericObj.optJSONArray("hideSelectors")),
                        closeButtonSelectors = jsonArrayToStrings(genericObj.optJSONArray("closeButtonSelectors"))
                    )
                } else {
                    GenericSelectors()
                }
                val sitesArray = root.optJSONArray("sites")
                val sites = mutableListOf<SiteEntry>()
                if (sitesArray != null) {
                    for (i in 0 until sitesArray.length()) {
                        val obj = sitesArray.optJSONObject(i) ?: continue
                        val origin = obj.optString("origin", "")
                        if (origin.isEmpty()) continue
                        sites.add(
                            SiteEntry(
                                origin = origin,
                                hideSelectors = jsonArrayToStrings(obj.optJSONArray("hideSelectors")),
                                closeButtonSelectors = jsonArrayToStrings(obj.optJSONArray("closeButtonSelectors")),
                                notes = obj.optString("notes", "").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }
                CleanupRegistry(version, generic, sites)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cleanup_registry.json; cleanup disabled", e)
                empty()
            }
        }

        private fun empty(): CleanupRegistry =
            CleanupRegistry(version = 0, generic = GenericSelectors(), siteEntries = emptyList())

        private fun jsonArrayToStrings(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val v = array.optString(i, "")
                if (v.isNotEmpty()) out.add(v)
            }
            return out
        }
    }
}
