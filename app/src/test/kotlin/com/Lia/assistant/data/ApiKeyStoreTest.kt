package com.Lia.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyStoreTest {
    @Test fun normalize_trimsWhitespaceAndNewlines() =
        assertEquals("abc123", ApiKeyStore.normalize("  abc123 \n"))

    @Test fun normalize_blankBecomesEmpty() =
        assertEquals("", ApiKeyStore.normalize("   "))
}
