package com.example.data.promotions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MailScanAwaiterTest {
    private fun status(request: String? = "scan", finished: String? = null, failed: Boolean = false) =
        MailImportRepository.ScanStatus(request, finished, null, null, failed)

    @Test fun waitsForTheRequestedScanBeforeReportingSuccess() = runTest {
        var reads=0
        var pauses=0
        awaitMailScan("scan", { reads++; status(finished=if(reads==3) "scan" else "older") }, {pauses++})
        assertEquals(3,reads)
        assertEquals(2,pauses)
    }

    @Test fun aFailedScanNeverReportsSuccess() = runTest {
        try {
            awaitMailScan("scan", {status(finished="scan",failed=true)}, {})
            fail("A failed import must be shown as an error")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("tentar novamente"))
        }
    }

    @Test fun aDifferentRequestCannotCompleteTheCurrentButtonAction() = runTest {
        try {
            awaitMailScan("scan", {status(request="other",finished="other")}, {})
            fail("A different request must not complete this scan")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Outra busca"))
        }
    }

    @Test fun unfinishedScanStopsCheckingAndAllowsManualRetry() = runTest {
        var reads=0
        try {
            awaitMailScan("scan", {reads++;status()}, {}, maxChecks=3)
            fail("An unfinished scan must not poll forever")
        } catch (expected: IllegalStateException) {
            assertEquals(3,reads)
            assertTrue(expected.message!!.contains("continuar"))
        }
    }
}
