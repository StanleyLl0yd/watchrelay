package com.sl.watchrelay.playback

import android.content.Context

class ExternalPlayerPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var targetPackage: String?
        get() = preferences.getString(KEY_TARGET_PACKAGE, null)?.takeIf(String::isNotBlank)
        set(value) {
            val editor = preferences.edit()
            if (value.isNullOrBlank()) editor.remove(KEY_TARGET_PACKAGE)
            else editor.putString(KEY_TARGET_PACKAGE, value)
            check(editor.commit()) { "Unable to persist external player selection" }
        }

    private companion object {
        const val PREFERENCES_NAME = "watchrelay_external_player"
        const val KEY_TARGET_PACKAGE = "target_package"
    }
}
