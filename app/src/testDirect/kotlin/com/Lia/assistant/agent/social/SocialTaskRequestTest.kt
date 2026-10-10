package com.Lia.assistant.agent.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialTaskRequestTest {
    private val shared = "content://com.Lia.assistant.direct.fileprovider/shared/photo.jpg"
    private val media = MediaResolver(object : SharedMediaSource {
        override fun isShared(uri: String) = uri == shared
    })

    private fun parse(vararg pairs: Pair<String, String?>) = SocialTaskRequest.parse(mapOf(*pairs), media)

    private fun invalid(result: ParseResult): ParseResult.Invalid {
        assertTrue("expected Invalid but was $result", result is ParseResult.Invalid)
        return result as ParseResult.Invalid
    }

    @Test fun aGoodRequestIsAccepted() {
        val r = parse(
            "platform" to "Instagram", "action" to "CREATE_POST", "media_uri" to shared,
            "caption" to "  Hello  ", "mode" to "PUBLISH", "target_account" to "lia.test",
        )
        val request = (r as ParseResult.Valid).request
        assertEquals(Platform.INSTAGRAM, request.platform)
        assertEquals(SocialAction.CREATE_POST, request.action)
        assertEquals(shared, request.mediaUri)
        assertEquals("Hello", request.caption)
        assertEquals(TaskMode.PUBLISH, request.mode)
        assertEquals("lia.test", request.targetAccount)
    }

    @Test fun aMissingModeMeansDraft() {
        val r = parse("platform" to "facebook", "action" to "create_post", "media_uri" to shared) as ParseResult.Valid
        assertEquals(TaskMode.DRAFT, r.request.mode)
    }

    @Test fun mediaMustBeContentUrisSharedToLia() {
        fun code(uri: String) = invalid(parse("platform" to "instagram", "action" to "create_post", "media_uri" to uri)).code
        assertEquals("media_required", code(""))
        assertEquals("media_scheme", code("file:///sdcard/DCIM/a.jpg"))
        assertEquals("media_scheme", code("/sdcard/DCIM/a.jpg"))
        assertEquals("media_scheme", code("https://example.com/a.jpg"))
        assertEquals("media_not_shared", code("content://media/external/images/1"))
    }

    @Test fun badPlatformActionAndModeAreRefused() {
        assertEquals("missing_platform", invalid(parse("action" to "create_post")).code)
        assertEquals("unknown_platform", invalid(parse("platform" to "tiktok", "action" to "create_post")).code)
        assertEquals("missing_action", invalid(parse("platform" to "instagram")).code)
        assertEquals("unknown_action", invalid(parse("platform" to "instagram", "action" to "delete_account")).code)
        assertEquals(
            "unknown_mode",
            invalid(parse("platform" to "instagram", "action" to "create_post", "media_uri" to shared, "mode" to "yolo")).code,
        )
    }

    @Test fun combinationsThatCannotWorkAreRefused() {
        assertEquals("unsupported_combination", invalid(parse("platform" to "instagram", "action" to "text_post", "caption" to "hi")).code)
        assertEquals(
            "draft_not_supported",
            invalid(parse("platform" to "instagram", "action" to "create_story", "media_uri" to shared, "mode" to "draft")).code,
        )
        assertEquals(
            "caption_not_supported",
            invalid(parse("platform" to "facebook", "action" to "create_story", "media_uri" to shared, "caption" to "x", "mode" to "publish")).code,
        )
    }

    @Test fun textPostsNeedTextAndTakeNoMedia() {
        assertEquals("caption_required", invalid(parse("platform" to "facebook", "action" to "text_post")).code)
        assertEquals(
            "media_not_allowed",
            invalid(parse("platform" to "facebook", "action" to "text_post", "caption" to "hi", "media_uri" to shared)).code,
        )
        val ok = parse("platform" to "facebook", "action" to "text_post", "caption" to "hi", "mode" to "publish") as ParseResult.Valid
        assertEquals(null, ok.request.mediaUri)
    }

    @Test fun captionLengthIsCheckedPerPlatform() {
        val long = "a".repeat(2201)
        assertEquals(
            "caption_too_long",
            invalid(parse("platform" to "instagram", "action" to "create_post", "media_uri" to shared, "caption" to long)).code,
        )
        assertTrue(
            parse("platform" to "facebook", "action" to "create_post", "media_uri" to shared, "caption" to long) is ParseResult.Valid,
        )
    }

    @Test fun theSharedMediaStoreKeepsOnlyTheNewestAndForgetsOnClear() {
        SharedMediaStore.clear()
        for (i in 1..25) SharedMediaStore.register("content://lia/$i")
        assertFalse(SharedMediaStore.isShared("content://lia/1"))
        assertTrue(SharedMediaStore.isShared("content://lia/25"))
        assertEquals("content://lia/25", SharedMediaStore.latest())
        SharedMediaStore.clear()
        assertFalse(SharedMediaStore.isShared("content://lia/25"))
    }
}
