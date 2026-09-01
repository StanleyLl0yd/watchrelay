package com.sl.watchrelay.settings

import android.content.Context
import com.sl.watchrelay.playback.PlaybackEngine

class AppSettings(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var watchedThresholdPercent: Int
        get() = preferences.getInt(KEY_WATCHED_THRESHOLD, DEFAULT_THRESHOLD_PERCENT)
            .coerceIn(MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT)
        set(value) {
            require(value in MIN_THRESHOLD_PERCENT..MAX_THRESHOLD_PERCENT)
            preferences.edit().putInt(KEY_WATCHED_THRESHOLD, value).apply()
        }

    var onboardingCompleted: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
        }

    val watchedThreshold: Double
        get() = watchedThresholdPercent / 100.0

    companion object {
        const val MIN_THRESHOLD_PERCENT = 50
        const val MAX_THRESHOLD_PERCENT = 100
        val DEFAULT_THRESHOLD_PERCENT = (PlaybackEngine.DEFAULT_WATCHED_THRESHOLD * 100).toInt()

        private const val PREFERENCES_NAME = "watchrelay_settings"
        private const val KEY_WATCHED_THRESHOLD = "watched_threshold_percent"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
