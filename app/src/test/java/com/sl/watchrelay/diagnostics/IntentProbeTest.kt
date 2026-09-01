package com.sl.watchrelay.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentProbeTest {
    @Test
    fun redactsSensitiveKeys() {
        assertEquals("<redacted>", IntentProbe.redact("stream_url", "https://example.invalid/video"))
    }

    @Test
    fun redactsUrlValuesEvenWithSafeKeys() {
        assertEquals("<redacted-url>", IntentProbe.redact("payload", "https://example.invalid/video"))
    }

    @Test
    fun preservesOrdinaryMetadata() {
        assertEquals("Fallout S02E03", IntentProbe.redact("title", "Fallout S02E03"))
    }
}
