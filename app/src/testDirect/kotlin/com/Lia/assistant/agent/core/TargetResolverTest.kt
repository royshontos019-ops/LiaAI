package com.Lia.assistant.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetResolverTest {

    private val resolver = TargetResolver()

    private fun element(
        id: Int,
        text: String = "",
        desc: String = "",
        viewId: String = "",
        className: String = "",
        clickable: Boolean = false,
        editable: Boolean = false,
        top: Int = id * 120,
    ) = UiElement(
        id = id,
        text = text,
        contentDescription = desc,
        viewId = viewId,
        className = className,
        bounds = Bounds(0, top, 1080, top + 100),
        clickable = clickable,
        enabled = true,
        editable = editable,
        scrollable = false,
        focused = false,
        password = false,
        depth = 0,
    )

    private fun screen(vararg elements: UiElement) = ScreenObservation(
        observationId = 1L,
        packageName = "com.example",
        windowClass = "Main",
        elements = elements.toList(),
        screenWidth = 1080,
        screenHeight = 2400,
        windowStamp = 1L,
    )

    private fun found(resolution: Resolution): UiElement = (resolution as Resolution.Found).element

    @Test
    fun exactTextBeatsPartialText() {
        val result = resolver.resolve(
            screen(element(0, text = "Share your post"), element(1, text = "Share")),
            TargetSpec("Share", texts = listOf("Share")),
        )
        assertEquals(1, found(result).id)
    }

    @Test
    fun matchesTheContentDescriptionToo() {
        val result = resolver.resolve(
            screen(element(0, desc = "Create new post", clickable = true)),
            TargetSpec("Create", texts = listOf("Create new post")),
        )
        assertEquals(0, found(result).id)
    }

    @Test
    fun theViewIdAloneCanMatch() {
        val result = resolver.resolve(
            screen(element(0, text = "x"), element(1, viewId = "com.example:id/share_button")),
            TargetSpec("Share", viewIdParts = listOf("share_button")),
        )
        assertEquals(1, found(result).id)
    }

    @Test
    fun exactModeRefusesPartialMatches() {
        val result = resolver.resolve(
            screen(element(0, text = "Share your post")),
            TargetSpec("Share", texts = listOf("Share"), exact = true),
        )
        assertTrue(result is Resolution.NotFound)
    }

    @Test
    fun nothingMatchingIsNotFound() {
        val result = resolver.resolve(screen(element(0, text = "Home")), TargetSpec("Share", texts = listOf("Share")))
        assertTrue(result is Resolution.NotFound)
    }

    @Test
    fun twoEqualMatchesAreAmbiguousWhenUniquenessIsRequired() {
        val spec = TargetSpec("Rahim", texts = listOf("Rahim"), requireUnique = true)
        val result = resolver.resolve(
            screen(element(0, text = "Rahim", clickable = true), element(1, text = "Rahim", clickable = true)),
            spec,
        )
        assertTrue(result is Resolution.Ambiguous)
        assertEquals(listOf(0, 1), (result as Resolution.Ambiguous).candidates.map { it.id })
    }

    @Test
    fun twoEqualMatchesPickTheFirstWhenUniquenessIsNotRequired() {
        val result = resolver.resolve(
            screen(element(0, text = "Rahim"), element(1, text = "Rahim")),
            TargetSpec("Rahim", texts = listOf("Rahim")),
        )
        assertEquals(0, found(result).id)
    }

    @Test
    fun aBetterMatchIsNotAmbiguous() {
        val result = resolver.resolve(
            screen(element(0, text = "Rahim Khan"), element(1, text = "Rahim")),
            TargetSpec("Rahim", texts = listOf("Rahim"), requireUnique = true),
        )
        assertEquals(1, found(result).id)
    }

    @Test
    fun mustBeClickableAndEditableFilters() {
        val screen = screen(
            element(0, text = "Caption", editable = true),
            element(1, text = "Caption", clickable = true),
        )
        assertEquals(1, found(resolver.resolve(screen, TargetSpec("c", texts = listOf("Caption"), mustBeClickable = true))).id)
        assertEquals(0, found(resolver.resolve(screen, TargetSpec("c", texts = listOf("Caption"), mustBeEditable = true))).id)
    }

    @Test
    fun aSpecWithOnlyFlagsMatchesByFlag() {
        val result = resolver.resolve(
            screen(element(0, text = "Title"), element(1, editable = true)),
            TargetSpec("any field", mustBeEditable = true),
        )
        assertEquals(1, found(result).id)
    }

    @Test
    fun regionsLookAtWhereTheElementSits() {
        val screen = screen(
            element(0, text = "Create", top = 100),
            element(1, text = "Create", top = 2200),
        )
        assertEquals(0, found(resolver.resolve(screen, TargetSpec("c", texts = listOf("Create"), region = ScreenRegion.TOP))).id)
        assertEquals(1, found(resolver.resolve(screen, TargetSpec("c", texts = listOf("Create"), region = ScreenRegion.BOTTOM))).id)
    }
}
