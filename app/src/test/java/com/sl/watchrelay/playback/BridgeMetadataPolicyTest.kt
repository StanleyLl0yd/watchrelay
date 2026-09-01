package com.sl.watchrelay.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeMetadataPolicyTest {
    @Test
    fun matchingPlayerConsumesFreshMetadata() {
        assertEquals(
            BridgeMetadataLookup.CONSUME,
            BridgeMetadataPolicy.lookup(
                storedTargetPackage = "org.videolan.vlc",
                createdAtMs = 1_000,
                requestedPackage = "org.videolan.vlc",
                nowMs = 2_000,
                maxAgeMs = 300_000,
            ),
        )
    }

    @Test
    fun unrelatedMediaSessionKeepsMetadataForTargetPlayer() {
        assertEquals(
            BridgeMetadataLookup.KEEP,
            BridgeMetadataPolicy.lookup(
                storedTargetPackage = "org.videolan.vlc",
                createdAtMs = 1_000,
                requestedPackage = "com.example.music",
                nowMs = 2_000,
                maxAgeMs = 300_000,
            ),
        )
    }

    @Test
    fun expiredOrClockInvalidMetadataIsDropped() {
        assertEquals(
            BridgeMetadataLookup.DROP,
            BridgeMetadataPolicy.lookup(
                storedTargetPackage = "org.videolan.vlc",
                createdAtMs = 1_000,
                requestedPackage = "org.videolan.vlc",
                nowMs = 400_000,
                maxAgeMs = 300_000,
            ),
        )
        assertEquals(
            BridgeMetadataLookup.DROP,
            BridgeMetadataPolicy.lookup(
                storedTargetPackage = "org.videolan.vlc",
                createdAtMs = 5_000,
                requestedPackage = "org.videolan.vlc",
                nowMs = 4_000,
                maxAgeMs = 300_000,
            ),
        )
    }
}
