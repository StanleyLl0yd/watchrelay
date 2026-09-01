package com.sl.watchrelay.matching

import android.content.Context
import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedPreferencesContentMappingStore(
    context: Context,
) : ContentMappingStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun get(signature: String): SavedContentMapping? = withContext(Dispatchers.IO) {
        preferences.getString(key(signature), null)?.let(ContentMappingCodec::decode)
    }

    override suspend fun put(signature: String, mapping: SavedContentMapping) {
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString(key(signature), ContentMappingCodec.encode(mapping)).commit()) {
                "Unable to persist content mapping"
            }
        }
    }

    override suspend fun remove(signature: String) {
        withContext(Dispatchers.IO) {
            check(preferences.edit().remove(key(signature)).commit()) {
                "Unable to remove content mapping"
            }
        }
    }

    private fun key(signature: String): String = sha256(signature)

    private companion object {
        const val PREFERENCES_NAME = "watchrelay_content_mappings_v1"

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

object ContentMappingCodec {
    fun encode(mapping: SavedContentMapping): String = listOf(
        VERSION,
        mapping.provider.name,
        mapping.mediaType.name,
        mapping.remoteId.toString(),
        mapping.showId?.toString().orEmpty(),
    ).joinToString("|")

    fun decode(value: String): SavedContentMapping? = runCatching {
        val parts = value.split('|')
        if (parts.size != 5 || parts[0] != VERSION) return null
        SavedContentMapping(
            provider = TrackerProvider.valueOf(parts[1]),
            mediaType = RemoteMediaType.valueOf(parts[2]),
            remoteId = parts[3].toInt(),
            showId = parts[4].takeIf(String::isNotBlank)?.toInt(),
        )
    }.getOrNull()

    private const val VERSION = "1"
}
