package com.Lia.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFlavorTest {
    @Test fun direct_exposesTools() {
        assertEquals("direct", FlavorRoutes.FLAVOR_NAME)
        assertTrue(LiaToolCatalog.tools().isNotEmpty())
    }

    @Test fun direct_declarationsMatchGeminiShape() {
        val decls = LiaToolCatalog.declarations()
        assertEquals(LiaToolCatalog.tools().size, decls.length())
        val d = decls.getJSONObject(0)
        assertEquals("device_action", d.getString("name"))
        val params = d.getJSONObject("parameters")
        assertEquals("OBJECT", params.getString("type"))
        assertEquals("STRING", params.getJSONObject("properties").getJSONObject("action").getString("type"))
    }
}
