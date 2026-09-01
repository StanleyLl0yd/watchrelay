package com.sl.watchrelay.matching

import android.content.Context
import com.sl.watchrelay.myshows.MyShowsContentCatalog
import com.sl.watchrelay.myshows.MyShowsFreeClient
import com.sl.watchrelay.security.KeystoreTokenStore

class ContentResolutionCoordinator(
    context: Context,
) {
    private val matcher = ContentMatcher(
        catalog = MyShowsContentCatalog(
            client = MyShowsFreeClient(),
            tokenStore = KeystoreTokenStore(context),
        ),
        mappings = SharedPreferencesContentMappingStore(context),
    )

    suspend fun resolve(metadata: PlaybackMetadata): ContentMatchResult = matcher.resolve(metadata)

    suspend fun confirm(
        metadata: PlaybackMetadata,
        candidate: ContentMatchCandidate,
    ): ContentMatchResult.Confirmed = matcher.confirm(metadata, candidate)

    suspend fun forget(metadata: PlaybackMetadata) = matcher.forget(metadata)
}
