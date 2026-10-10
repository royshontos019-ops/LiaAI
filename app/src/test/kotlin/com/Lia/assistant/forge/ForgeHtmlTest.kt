package com.Lia.assistant.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeHtmlTest {
    // ---- fences ----
    @Test fun aHtmlFenceIsRemoved() =
        assertEquals("<p>a</p>", ForgeHtml.stripFences("```html\n<p>a</p>\n```"))

    @Test fun aPlainFenceIsRemoved() =
        assertEquals("<p>a</p>", ForgeHtml.stripFences("```\n<p>a</p>\n```"))

    @Test fun textWithoutFencesIsKept() =
        assertEquals("<p>a</p>", ForgeHtml.stripFences("  <p>a</p>  "))

    @Test fun aFenceThatHasOnlyJustStartedGivesNothing() =
        assertEquals("", ForgeHtml.stripFences("```ht"))

    @Test fun cleanDropsChatterBeforeAndTextAfterThePage() {
        val raw = "Sure! Here is your site:\n<!DOCTYPE html><html><body>x</body></html>\nHope you like it."
        assertEquals("<!DOCTYPE html><html><body>x</body></html>", ForgeHtml.clean(raw))
    }

    @Test fun cleanHandlesFencesAndChatterTogether() {
        val raw = "```html\n<!DOCTYPE html><html><body>x</body></html>\n```"
        assertEquals("<!DOCTYPE html><html><body>x</body></html>", ForgeHtml.clean(raw))
    }

    // ---- looksLikeHtml / isComplete ----
    @Test fun looksLikeHtml() {
        assertTrue(ForgeHtml.looksLikeHtml("<!DOCTYPE html><html></html>"))
        assertTrue(ForgeHtml.looksLikeHtml("<html lang=\"en\"><body>"))
        assertTrue(ForgeHtml.looksLikeHtml("```html\n<!doctype html>\n<html>"))
        assertTrue(ForgeHtml.looksLikeHtml("Here you go:\n<!DOCTYPE html><html>"))
        assertFalse(ForgeHtml.looksLikeHtml("I cannot do that."))
        assertFalse(ForgeHtml.looksLikeHtml("<div>just a fragment</div>"))
        assertFalse(ForgeHtml.looksLikeHtml(""))
    }

    @Test fun isCompleteNeedsTheClosingTag() {
        assertTrue(ForgeHtml.isComplete("<html><body></body></html>\n"))
        assertTrue(ForgeHtml.isComplete("<HTML></HTML>"))
        assertFalse(ForgeHtml.isComplete("<html><body>"))
    }

    // ---- truncation repair ----
    @Test fun aCutOffTagIsDroppedAndTheEndIsClosed() {
        val fixed = ForgeHtml.repairTruncated("<!DOCTYPE html><html><body><p>Hi</p><div cla")
        assertTrue(fixed.contains("<p>Hi</p>"))
        assertFalse(fixed.contains("cla"))
        assertTrue(fixed.contains("</body>"))
        assertTrue(fixed.trimEnd().endsWith("</html>"))
    }

    @Test fun anUnfinishedScriptIsRemovedWhole() {
        val fixed = ForgeHtml.repairTruncated("<html><body><p>Hi</p><script>let a = 1; fu")
        assertFalse(fixed.contains("<script"))
        assertTrue(fixed.contains("<p>Hi</p>"))
        assertTrue(fixed.trimEnd().endsWith("</html>"))
    }

    @Test fun aFinishedScriptStaysWhenALaterOneIsCutOff() {
        val fixed = ForgeHtml.repairTruncated("<html><body><script>var a=1;</script><script>var b=")
        assertTrue(fixed.contains("var a=1;"))
        assertFalse(fixed.contains("var b="))
    }

    @Test fun anUnfinishedStyleIsCutBackToItsLastWholeRule() {
        val fixed = ForgeHtml.repairTruncated("<html><head><style>.a{color:red}.b{col")
        assertTrue(fixed.contains(".a{color:red}"))
        assertFalse(fixed.contains(".b{col"))
        assertTrue(fixed.contains("</style>"))
        assertTrue(fixed.trimEnd().endsWith("</html>"))
    }

    @Test fun aCompletePageIsLeftAlone() {
        val page = "<!DOCTYPE html><html><body><script>var a=1;</script></body></html>"
        assertEquals(page, ForgeHtml.repairTruncated(page))
    }

    @Test fun aPageMissingOnlyTheLastTagsGetsThem() {
        val fixed = ForgeHtml.repairTruncated("<html><body><p>Hi</p>")
        assertEquals("<html><body><p>Hi</p>\n</body>\n</html>", fixed)
    }

    // ---- safety net ----
    @Test fun theSafetyNetGoesBeforeTheClosingBody() {
        val out = ForgeHtml.withSafetyNet("<html><body><p>Hi</p></body></html>")
        assertTrue(out.contains("id=\"${ForgeHtml.SAFETY_NET_ID}\""))
        assertTrue(out.indexOf(ForgeHtml.SAFETY_NET_ID) < out.indexOf("</body>"))
        assertTrue(out.trimEnd().endsWith("</html>"))
    }

    @Test fun theSafetyNetIsAddedOnlyOnce() {
        val once = ForgeHtml.withSafetyNet("<html><body><p>Hi</p></body></html>")
        val twice = ForgeHtml.withSafetyNet(once)
        assertEquals(once, twice)
        assertEquals(1, Regex("id=\"${ForgeHtml.SAFETY_NET_ID}\"").findAll(twice).count())
    }

    @Test fun theSafetyNetIsAppendedWhenThereIsNoBodyEnd() {
        val out = ForgeHtml.withSafetyNet("<html><body><p>Hi</p>")
        assertTrue(out.contains(ForgeHtml.SAFETY_NET_ID))
    }

    @Test fun theSafetyNetSkipsWhatItMustNotTouch() {
        val net = ForgeHtml.withSafetyNet("<body></body>")
        assertTrue(net.contains("1400"))
        assertTrue(net.contains("CANVAS"))
        assertTrue(net.contains("'fixed'"))
        assertTrue(net.contains("pointerEvents"))
        assertTrue(net.contains("aria-hidden"))
        assertTrue(net.contains("opacity"))
    }
}
