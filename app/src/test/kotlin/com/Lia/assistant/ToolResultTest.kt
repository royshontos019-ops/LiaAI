package com.Lia.assistant

import org.junit.Assert.assertFalse
import org.junit.Test

class ToolResultTest {
    @Test fun unavailableIsNotOk() = assertFalse(ToolResult.unavailable("X").ok)
}
