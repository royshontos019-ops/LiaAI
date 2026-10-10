package com.Lia.assistant

import com.Lia.assistant.agent.social.SharedMediaStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AgentMediaArgTest {
    @Before fun setUp() = SharedMediaStore.clear()

    @After fun tearDown() = SharedMediaStore.clear()

    @Test fun latestMeansTheNewestSharedFile() {
        SharedMediaStore.register("content://lia/one")
        SharedMediaStore.register("content://lia/two")
        assertEquals("content://lia/two", AgentTools.resolveMediaArg("latest", "create_post"))
        assertEquals("content://lia/two", AgentTools.resolveMediaArg("Shared", "create_reel"))
        assertEquals("content://lia/two", AgentTools.resolveMediaArg("", "create_story"))
    }

    @Test fun anExplicitAddressIsLeftAloneForTheParserToCheck() =
        assertEquals("file:///sdcard/a.jpg", AgentTools.resolveMediaArg("file:///sdcard/a.jpg", "create_post"))

    @Test fun aTextPostNeverGetsMedia() {
        SharedMediaStore.register("content://lia/one")
        assertEquals("", AgentTools.resolveMediaArg("", "text_post"))
    }

    @Test fun nothingSharedMeansNothing() = assertEquals("", AgentTools.resolveMediaArg("latest", "create_post"))
}
