package com.sl.watchrelay.matching

import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.SyncMutation
import com.sl.watchrelay.sync.TrackerProvider
import com.sl.watchrelay.sync.TrackerTarget

class ContentMatcher(
    private val catalog: ContentCatalog,
    private val mappings: ContentMappingStore,
) {
    suspend fun resolve(source: PlaybackMetadata): ContentMatchResult {
        val metadata = MetadataNormalizer.normalize(source)
        if (metadata.mappingSignature.isBlank()) {
            return ContentMatchResult.Unresolved("Playback metadata is empty")
        }

        mappings.get(metadata.mappingSignature)?.let { saved ->
            return resolveSaved(saved, MatchEvidence.USER_MAPPING, 100)
        }

        return when (metadata.mediaKind) {
            SourceMediaKind.MOVIE -> resolveMovie(metadata)
            SourceMediaKind.EPISODE -> resolveEpisode(metadata)
            SourceMediaKind.UNKNOWN -> ContentMatchResult.Unresolved(
                "Media type is unknown; automatic matching is disabled",
            )
        }
    }

    suspend fun confirm(
        source: PlaybackMetadata,
        candidate: ContentMatchCandidate,
    ): ContentMatchResult.Confirmed {
        val metadata = MetadataNormalizer.normalize(source)
        validateCandidateForSource(metadata, candidate)
        mappings.put(metadata.mappingSignature, candidate.mapping)
        return resolveSaved(candidate.mapping, MatchEvidence.USER_MAPPING, 100)
    }

    suspend fun forget(source: PlaybackMetadata) {
        mappings.remove(MetadataNormalizer.normalize(source).mappingSignature)
    }

    private suspend fun resolveMovie(metadata: NormalizedMetadata): ContentMatchResult {
        externalCandidate(metadata)?.let { candidate ->
            if (isExternalCompatible(metadata, candidate)) {
                return confirmedMovie(candidate, 100, MatchEvidence.EXTERNAL_ID)
            }
        }

        val candidates = searchQueries(metadata)
            .flatMap { query -> catalog.searchMovies(query, metadata.year) }
            .distinctBy(CatalogItem::id)
            .map { item -> item to score(metadata, item) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }

        return selectMovieResult(candidates)
    }

    private suspend fun resolveEpisode(metadata: NormalizedMetadata): ContentMatchResult {
        val season = metadata.season
            ?: return ContentMatchResult.Unresolved("Season number is required for episode matching")
        val episode = metadata.episode
            ?: return ContentMatchResult.Unresolved("Episode number is required for episode matching")

        val external = externalCandidate(metadata)
        if (external != null && isExternalCompatible(metadata, external)) {
            return resolveEpisodeInShow(
                show = external,
                season = season,
                episode = episode,
                confidence = 100,
                evidence = MatchEvidence.EXTERNAL_ID,
            )
        }

        val shows = searchQueries(metadata)
            .flatMap(catalog::searchShows)
            .distinctBy(CatalogItem::id)
            .map { item -> item to score(metadata, item) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }

        if (shows.isEmpty()) return ContentMatchResult.Unresolved("No matching show was found")

        val top = shows.first()
        val runnerUp = shows.getOrNull(1)
        if (canAutoConfirm(top.second, runnerUp?.second)) {
            return resolveEpisodeInShow(
                show = top.first,
                season = season,
                episode = episode,
                confidence = top.second,
                evidence = MatchEvidence.TITLE_AND_YEAR,
            )
        }

        val candidates = shows.take(MAX_AMBIGUOUS_SHOWS).mapNotNull { (show, confidence) ->
            val matchedEpisode = catalog.episodes(show.id)
                .filter { it.season == season && it.episode == episode }
                .singleOrNull()
                ?: return@mapNotNull null
            ContentMatchCandidate(
                mapping = SavedContentMapping(
                    provider = TrackerProvider.MYSHOWS,
                    mediaType = RemoteMediaType.EPISODE,
                    remoteId = matchedEpisode.episodeId,
                    showId = show.id,
                ),
                title = show.title,
                originalTitle = show.originalTitle,
                year = show.year,
                season = season,
                episode = episode,
                confidence = confidence,
            )
        }

        return if (candidates.isEmpty()) {
            ContentMatchResult.Unresolved("The requested episode was not found in matching shows")
        } else {
            ContentMatchResult.Ambiguous(
                candidates = candidates,
                reason = "Multiple shows are plausible; user confirmation is required",
            )
        }
    }

    private suspend fun selectMovieResult(
        candidates: List<Pair<CatalogItem, Int>>,
    ): ContentMatchResult {
        if (candidates.isEmpty()) return ContentMatchResult.Unresolved("No matching movie was found")
        val top = candidates.first()
        val runnerUp = candidates.getOrNull(1)
        if (canAutoConfirm(top.second, runnerUp?.second)) {
            return confirmedMovie(top.first, top.second, MatchEvidence.TITLE_AND_YEAR)
        }

        return ContentMatchResult.Ambiguous(
            candidates = candidates.take(MAX_AMBIGUOUS_SHOWS).map { (item, confidence) ->
                ContentMatchCandidate(
                    mapping = SavedContentMapping(
                        provider = TrackerProvider.MYSHOWS,
                        mediaType = RemoteMediaType.MOVIE,
                        remoteId = item.id,
                    ),
                    title = item.title,
                    originalTitle = item.originalTitle,
                    year = item.year,
                    confidence = confidence,
                )
            },
            reason = "Movie match confidence is insufficient for automatic synchronization",
        )
    }

    private suspend fun confirmedMovie(
        movie: CatalogItem,
        confidence: Int,
        evidence: MatchEvidence,
    ): ContentMatchResult.Confirmed {
        val previous = catalog.previousMovieState(movie.id)
        return ContentMatchResult.Confirmed(
            content = ResolvedContent(
                target = TrackerTarget(
                    provider = TrackerProvider.MYSHOWS,
                    mediaType = RemoteMediaType.MOVIE,
                    remoteId = movie.id,
                    previousRemoteState = previous,
                ),
                title = movie.title,
                originalTitle = movie.originalTitle,
                year = movie.year,
            ),
            confidence = confidence,
            evidence = evidence,
        )
    }

    private suspend fun resolveEpisodeInShow(
        show: CatalogItem,
        season: Int,
        episode: Int,
        confidence: Int,
        evidence: MatchEvidence,
    ): ContentMatchResult {
        val matches = catalog.episodes(show.id)
            .filter { it.season == season && it.episode == episode }
        if (matches.isEmpty()) {
            return ContentMatchResult.Unresolved("S%02dE%02d was not found in MyShows".format(season, episode))
        }
        if (matches.size > 1) {
            return ContentMatchResult.Ambiguous(
                candidates = matches.map { item ->
                    ContentMatchCandidate(
                        mapping = SavedContentMapping(
                            provider = TrackerProvider.MYSHOWS,
                            mediaType = RemoteMediaType.EPISODE,
                            remoteId = item.episodeId,
                            showId = show.id,
                        ),
                        title = show.title,
                        originalTitle = show.originalTitle,
                        year = show.year,
                        season = season,
                        episode = episode,
                        confidence = confidence,
                    )
                },
                reason = "MyShows returned duplicate season/episode coordinates",
            )
        }

        val matched = matches.single()
        val previous = catalog.previousEpisodeState(show.id, matched.episodeId)
        return ContentMatchResult.Confirmed(
            content = ResolvedContent(
                target = TrackerTarget(
                    provider = TrackerProvider.MYSHOWS,
                    mediaType = RemoteMediaType.EPISODE,
                    remoteId = matched.episodeId,
                    previousRemoteState = previous,
                ),
                title = show.title,
                originalTitle = show.originalTitle,
                year = show.year,
                season = season,
                episode = episode,
            ),
            confidence = confidence,
            evidence = evidence,
        )
    }

    private suspend fun resolveSaved(
        mapping: SavedContentMapping,
        evidence: MatchEvidence,
        confidence: Int,
    ): ContentMatchResult.Confirmed {
        val previous = when (mapping.mediaType) {
            RemoteMediaType.MOVIE -> catalog.previousMovieState(mapping.remoteId)
            RemoteMediaType.EPISODE -> catalog.previousEpisodeState(
                showId = requireNotNull(mapping.showId),
                episodeId = mapping.remoteId,
            )
        }
        return ContentMatchResult.Confirmed(
            content = ResolvedContent(
                target = TrackerTarget(
                    provider = mapping.provider,
                    mediaType = mapping.mediaType,
                    remoteId = mapping.remoteId,
                    previousRemoteState = previous,
                ),
                title = null,
                originalTitle = null,
                year = null,
            ),
            confidence = confidence,
            evidence = evidence,
        )
    }

    private suspend fun externalCandidate(metadata: NormalizedMetadata): CatalogItem? {
        metadata.imdbId?.let { id ->
            catalog.findByExternalId("imdb", id)?.let { return it }
        }
        metadata.kinopoiskId?.let { id ->
            catalog.findByExternalId("kinopoisk", id)?.let { return it }
        }
        return null
    }

    private fun isExternalCompatible(metadata: NormalizedMetadata, candidate: CatalogItem): Boolean {
        if (metadata.year != null && candidate.year != null && kotlin.math.abs(metadata.year - candidate.year) > 1) {
            return false
        }
        return true
    }

    private fun searchQueries(metadata: NormalizedMetadata): List<String> = listOfNotNull(
        metadata.originalTitle,
        metadata.title,
    ).map(String::trim).filter(String::isNotBlank).distinct()

    private fun score(metadata: NormalizedMetadata, candidate: CatalogItem): Int {
        val sourceKeys = listOfNotNull(metadata.originalTitleKey, metadata.titleKey).distinct()
        val candidateKeys = listOfNotNull(
            candidate.originalTitle?.let(MetadataNormalizer::comparisonKey),
            candidate.title?.let(MetadataNormalizer::comparisonKey),
        ).distinct()
        if (sourceKeys.isEmpty() || candidateKeys.isEmpty()) return 0

        val titleScore = when {
            sourceKeys.any { it in candidateKeys } -> 80
            sourceKeys.any { source -> candidateKeys.any { candidateKey -> similarTitle(source, candidateKey) } } -> 60
            else -> return 0
        }

        val yearScore = when {
            metadata.year == null || candidate.year == null -> 0
            metadata.year == candidate.year -> 20
            kotlin.math.abs(metadata.year - candidate.year) == 1 && metadata.mediaKind == SourceMediaKind.EPISODE -> 10
            else -> -40
        }
        return (titleScore + yearScore).coerceIn(0, 100)
    }

    private fun similarTitle(left: String, right: String): Boolean {
        if (left.length < 5 || right.length < 5) return false
        val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val union = leftTokens.union(rightTokens).size.toDouble()
        return union > 0 && intersection / union >= 0.8
    }

    private fun canAutoConfirm(top: Int, runnerUp: Int?): Boolean =
        top >= AUTO_CONFIRM_SCORE && (runnerUp == null || top - runnerUp >= AUTO_CONFIRM_MARGIN)

    private fun validateCandidateForSource(metadata: NormalizedMetadata, candidate: ContentMatchCandidate) {
        when (metadata.mediaKind) {
            SourceMediaKind.MOVIE -> require(candidate.mapping.mediaType == RemoteMediaType.MOVIE)
            SourceMediaKind.EPISODE -> {
                require(candidate.mapping.mediaType == RemoteMediaType.EPISODE)
                requireNotNull(candidate.mapping.showId)
            }
            SourceMediaKind.UNKNOWN -> error("Cannot confirm a mapping while media type is unknown")
        }
    }

    private companion object {
        const val AUTO_CONFIRM_SCORE = 90
        const val AUTO_CONFIRM_MARGIN = 15
        const val MAX_AMBIGUOUS_SHOWS = 8
    }
}
