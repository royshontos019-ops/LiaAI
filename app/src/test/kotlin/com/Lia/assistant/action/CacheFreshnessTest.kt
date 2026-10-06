package com.Lia.assistant.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheFreshnessTest {
    private val ttl = InstalledAppLabelCache.TTL_MS

    @Test fun ttlIsFifteenMinutes() = assertTrue(ttl == 15 * 60 * 1000L)

    @Test fun neverLoadedIsNotFresh() = assertFalse(InstalledAppLabelCache.isFresh(-1L, 1_000L))

    @Test fun freshJustBeforeTheTtlAndStaleAtIt() {
        assertTrue(InstalledAppLabelCache.isFresh(1_000L, 1_000L + ttl - 1))
        assertFalse(InstalledAppLabelCache.isFresh(1_000L, 1_000L + ttl))
    }
}
