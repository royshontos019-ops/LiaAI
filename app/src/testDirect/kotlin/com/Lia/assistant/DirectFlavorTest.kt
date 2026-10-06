package com.Lia.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFlavorTest {
    @Test fun direct_exposesTools() {
        assertEquals("direct", FlavorRoutes.FLAVOR_NAME)
        assertTrue(LiaToolCatalog.tools().isNotEmpty())
    }
}
