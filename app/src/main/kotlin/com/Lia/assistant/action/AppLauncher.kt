package com.Lia.assistant.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

data class AppMatch(val label: String, val packageName: String)

object AppLauncher {
    private const val MIN_KEY_LENGTH = 3
    private const val MIN_QUERY_LENGTH = 2
    private val WHITESPACE = Regex("\\s+")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    internal fun normalize(s: String): String = s.trim().lowercase().replace(WHITESPACE, " ")

    private fun words(s: String): List<String> = s.split(WORD_SPLIT).filter { it.isNotEmpty() }

    /**
     * [labels] maps lowercase label -> package. Order of preference:
     *  1. exact label match;
     *  2. among labels of length >= 3: label starts with the query, or the query starts with the
     *     label, or every query word is a prefix of some label word. The label whose length is
     *     closest to the query's wins.
     * There is deliberately NO loose substring matching ("app" never finds "whatsapp").
     */
    fun findApp(labels: Map<String, String>, query: String): AppMatch? {
        val q = normalize(query)
        if (q.length < MIN_QUERY_LENGTH) return null
        labels[q]?.let { return AppMatch(q, it) }

        val queryWords = words(q)
        if (queryWords.isEmpty()) return null

        var best: Map.Entry<String, String>? = null
        for (entry in labels) {
            val key = entry.key
            if (key.length < MIN_KEY_LENGTH) continue
            val keyWords = words(key)
            val matches = key.startsWith(q) ||
                q.startsWith(key) ||
                queryWords.all { qw -> keyWords.any { it.startsWith(qw) } }
            if (!matches) continue
            if (best == null || isCloser(key, best.key, q)) best = entry
        }
        return best?.let { AppMatch(it.key, it.value) }
    }

    /** True if [candidate] is a better length fit for [query] than [current]. Ties are stable. */
    private fun isCloser(candidate: String, current: String, query: String): Boolean {
        val dc = kotlin.math.abs(candidate.length - query.length)
        val dk = kotlin.math.abs(current.length - query.length)
        if (dc != dk) return dc < dk
        if (candidate.length != current.length) return candidate.length < current.length
        return candidate < current
    }

    /** Starts the app's launcher activity. Returns false if it cannot be started. */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
