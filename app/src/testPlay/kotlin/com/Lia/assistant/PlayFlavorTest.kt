package com.Lia.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayFlavorTest {
    @Test fun play_hasNoTools() {
        assertEquals("play", FlavorRoutes.FLAVOR_NAME)
        assertTrue(LiaToolCatalog.tools().isEmpty())
        assertFalse(ActionExecutor.isAvailable)
    }

    @Test fun play_declaresNoFunctions() = assertEquals(0, LiaToolCatalog.declarations().length())
}
