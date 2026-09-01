package com.sl.watchrelay.diagnostics

import android.content.Intent
import android.net.Uri

data class IntentProbeSnapshot(
    val action: String?,
    val mimeType: String?,
    val dataScheme: String?,
    val categories: List<String>,
    val extras: List<Pair<String, String>>,
)

object IntentProbe {
    fun inspect(intent: Intent): IntentProbeSnapshot {
        val extras = intent.extras?.keySet().orEmpty().sorted().map { key ->
            key to redact(key, intent.extras?.get(key))
        }
        return IntentProbeSnapshot(
            action = intent.action,
            mimeType = intent.type,
            dataScheme = intent.data?.scheme,
            categories = intent.categories?.sorted().orEmpty(),
            extras = extras,
        )
    }

    internal fun redact(key: String, value: Any?): String {
        if (SENSITIVE_KEY.containsMatchIn(key)) return "<redacted>"
        return when (value) {
            null -> "null"
            is Uri -> "Uri(${value.scheme ?: "unknown"})"
            is String -> redactString(value)
            is CharSequence -> redactString(value.toString())
            is Number, is Boolean -> value.toString()
            else -> "<${value.javaClass.simpleName}>"
        }
    }

    private fun redactString(value: String): String {
        val trimmed = value.trim()
        if (SENSITIVE_VALUE.containsMatchIn(trimmed)) return "<redacted-url>"
        return trimmed.take(MAX_VALUE_LENGTH)
    }

    private val SENSITIVE_KEY = Regex(
        "(?i)(url|uri|token|auth|cookie|password|secret|stream|source|data)",
    )
    private val SENSITIVE_VALUE = Regex("(?i)^(https?://|magnet:|content://|file://)")
    private const val MAX_VALUE_LENGTH = 160
}
