package ru.lisurgut.oalab

import org.junit.Assert.*
import org.junit.Test

class UpdatePollingPolicyTest {
    @Test fun fiveSecondUiDoesNotOverloadGithub() {
        assertEquals(5_000L, UpdatePollingPolicy.UI_REFRESH_MS)
        assertTrue(UpdatePollingPolicy.shouldRequest(1000, 0, false, false, false))
        assertFalse(UpdatePollingPolicy.shouldRequest(5_000, 0, true, false, false))
        assertFalse(UpdatePollingPolicy.shouldRequest(120_000, 0, true, false, false))
        assertTrue(UpdatePollingPolicy.shouldRequest(300_000, 0, true, false, false))
    }

    @Test fun retryAndRefresh() {
        assertFalse(UpdatePollingPolicy.shouldRequest(119_000, 0, true, true, false))
        assertTrue(UpdatePollingPolicy.shouldRequest(120_000, 0, true, true, false))
        assertTrue(UpdatePollingPolicy.shouldRequest(5_000, 0, true, false, true))
        assertTrue(UpdatePollingPolicy.shouldRequest(2_000, 8_000, true, false, false))
    }

    @Test fun semanticVersionComparison() {
        assertTrue(UpdatePollingPolicy.isNewer("v0.6.0", "0.5.3"))
        assertFalse(UpdatePollingPolicy.isNewer("v0.5.3", "0.5.3"))
        assertFalse(UpdatePollingPolicy.isNewer("v0.5.2", "0.5.3"))
        assertTrue(UpdatePollingPolicy.isNewer("v1.0.0", "0.99.99"))
    }
}
