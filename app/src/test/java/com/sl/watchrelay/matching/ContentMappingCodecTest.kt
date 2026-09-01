package com.sl.watchrelay.matching

import com.sl.watchrelay.sync.RemoteMediaType
import com.sl.watchrelay.sync.TrackerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentMappingCodecTest {
    @Test
    fun movieMappingRoundTrips() {
        val mapping = SavedContentMapping(
            provider = TrackerProvider.MYSHOWS,
            mediaType = RemoteMediaType.MOVIE,
            remoteId = 42,
        )

        assertEquals(mapping, ContentMappingCodec.decode(ContentMappingCodec.encode(mapping)))
    }

    @Test
    fun episodeMappingRoundTripsWithShowId() {
        val mapping = SavedContentMapping(
            provider = TrackerProvider.MYSHOWS,
            mediaType = RemoteMediaType.EPISODE,
            remoteId = 203,
            showId = 20,
        )

        assertEquals(mapping, ContentMappingCodec.decode(ContentMappingCodec.encode(mapping)))
    }

    @Test
    fun malformedMappingIsIgnored() {
        assertNull(ContentMappingCodec.decode("invalid"))
        assertNull(ContentMappingCodec.decode("1|MYSHOWS|EPISODE|203|"))
    }
}
