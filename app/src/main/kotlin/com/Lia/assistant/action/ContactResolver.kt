package com.Lia.assistant.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

sealed interface ContactLookup {
    data class Found(val displayName: String, val number: String) : ContactLookup
    data object NoPermission : ContactLookup
    data object NotFound : ContactLookup
}

data class ContactCandidate(val name: String, val number: String)

object ContactResolver {
    private const val MAX_ROWS = 5
    private const val MIN_DIGITS = 5
    private const val PHONE_CHARS = "0123456789 +-()."

    /** Digits, spaces and + - ( ) . only, with at least five digits. */
    fun looksLikePhoneNumber(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty() || q.any { it !in PHONE_CHARS }) return false
        return q.count { it in '0'..'9' } >= MIN_DIGITS
    }

    /** Keeps ASCII digits, and a leading + if there was one. */
    fun normalizeNumber(raw: String): String {
        val plus = raw.trim().startsWith("+")
        return (if (plus) "+" else "") + raw.filter { it in '0'..'9' }
    }

    /** A number typed or spoken directly needs no contact lookup. */
    fun directNumber(query: String): ContactLookup.Found? =
        if (looksLikePhoneNumber(query)) ContactLookup.Found(query.trim(), normalizeNumber(query)) else null

    /** Best candidate: exact name > name starts with query > name contains query; shorter name first. */
    fun rank(query: String, candidates: List<ContactCandidate>): ContactCandidate? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return candidates
            .filter { tier(it.name, q) < NO_MATCH }
            .minWithOrNull(
                compareBy<ContactCandidate>({ tier(it.name, q) }, { it.name.length }, { it.name.lowercase() }),
            )
    }

    private const val NO_MATCH = 3

    private fun tier(name: String, q: String): Int = when {
        name.equals(q, ignoreCase = true) -> 0
        name.startsWith(q, ignoreCase = true) -> 1
        name.contains(q, ignoreCase = true) -> 2
        else -> NO_MATCH
    }

    /** Blocking (contacts query): call from a background thread. */
    fun lookup(context: Context, query: String): ContactLookup {
        directNumber(query)?.let { return it }
        val q = query.trim()
        if (q.isEmpty()) return ContactLookup.NotFound
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ContactLookup.NoPermission
        }
        val candidates = try {
            queryContacts(context, q)
        } catch (_: SecurityException) {
            return ContactLookup.NoPermission
        }
        val best = rank(q, candidates) ?: return ContactLookup.NotFound
        return ContactLookup.Found(best.name, normalizeNumber(best.number))
    }

    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun queryContacts(context: Context, name: String): List<ContactCandidate> {
        val out = ArrayList<ContactCandidate>(MAX_ROWS)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
            arrayOf("%${escapeLike(name)}%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameCol < 0 || numberCol < 0) return emptyList()
            while (out.size < MAX_ROWS && cursor.moveToNext()) {
                val n = cursor.getString(nameCol).orEmpty()
                val num = cursor.getString(numberCol).orEmpty()
                if (n.isNotEmpty() && num.isNotEmpty()) out += ContactCandidate(n, num)
            }
        }
        return out
    }
}
