package com.Lia.assistant.agent.social

/** The set of media the user has shared to LIA (through ShareToLiaActivity). */
interface SharedMediaSource {
    fun isShared(uri: String): Boolean
}

sealed interface MediaResolution {
    data class Allowed(val uri: String) : MediaResolution
    data class Rejected(val code: String, val message: String) : MediaResolution
}

/**
 * Lets only content:// URIs through, and only ones the user shared to LIA. The model can never
 * name a file path, a web address or some other app's file.
 */
class MediaResolver(private val source: SharedMediaSource) {
    fun resolve(rawUri: String): MediaResolution {
        val uri = rawUri.trim()
        if (uri.isEmpty()) return MediaResolution.Rejected("media_required", "No media was given. Ask the user to share the photo or video to Lia first, using the Share menu.")
        if (!uri.startsWith("content://", ignoreCase = true)) {
            return MediaResolution.Rejected(
                "media_scheme",
                "Only media the user shared to Lia can be used. Ask the user to share the photo or video to Lia first.",
            )
        }
        if (!source.isShared(uri)) {
            return MediaResolution.Rejected(
                "media_not_shared",
                "That file was not shared to Lia. Ask the user to share it to Lia first.",
            )
        }
        return MediaResolution.Allowed(uri)
    }
}

/** Production source: URIs that ShareToLiaActivity registered. Keeps the newest few only. */
object SharedMediaStore : SharedMediaSource {
    private const val MAX = 20
    private val uris = LinkedHashSet<String>()

    @Synchronized
    fun register(uri: String) {
        uris.remove(uri)
        uris.add(uri)
        while (uris.size > MAX) uris.remove(uris.first())
    }

    @Synchronized
    fun latest(): String? = uris.lastOrNull()

    @Synchronized
    override fun isShared(uri: String): Boolean = uri in uris

    @Synchronized
    fun clear() = uris.clear()
}
