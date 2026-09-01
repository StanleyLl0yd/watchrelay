package com.sl.watchrelay.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun reportContainsStatusButNoMediaOrCredentialFields() {
        val report = DiagnosticReport.build(
            DiagnosticReportData(
                appVersion = "0.2.0",
                sdkInt = 36,
                device = "Example Device",
                notificationAccess = true,
                myShowsConnected = true,
                pendingSyncCount = 2,
                authRequiredCount = 1,
                failedSyncCount = 3,
                watchedThresholdPercent = 80,
            ),
        )

        assertTrue(report.contains("pending_sync=2"))
        assertTrue(report.contains("credentials=not_exported"))
        assertTrue(report.contains("history_titles=not_exported"))
        assertFalse(report.contains("token="))
        assertFalse(report.contains("http://"))
        assertFalse(report.contains("https://"))
    }
}
