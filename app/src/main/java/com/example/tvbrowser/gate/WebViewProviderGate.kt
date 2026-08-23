package com.example.tvbrowser.gate

import android.content.Context
import android.util.Log
import androidx.webkit.WebViewCompat

object WebViewProviderGate {

    const val MINIMUM_MAJOR_VERSION = 110
    private const val TAG = "WebViewProviderGate"

    enum class Action {
        BLOCKING_ERROR,
        WARNING,
        PROCEED
    }

    data class Result(
        val available: Boolean,
        val packageName: String?,
        val versionName: String?,
        val majorVersion: Int
    )

    fun check(context: Context): Result {
        val info = WebViewCompat.getCurrentWebViewPackage(context)
            ?: return Result(false, null, null, 0)
        val major = info.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
        return Result(true, info.packageName, info.versionName, major)
    }

    fun classify(result: Result): Action = when {
        !result.available -> Action.BLOCKING_ERROR
        result.majorVersion < MINIMUM_MAJOR_VERSION -> Action.WARNING
        else -> Action.PROCEED
    }

    fun evaluate(context: Context): Pair<Result, Action> {
        val result = check(context)
        val action = classify(result)
        Log.i(TAG, "gate: available=${result.available} major=${result.majorVersion} " +
            "action=$action")
        return result to action
    }
}
