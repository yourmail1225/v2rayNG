package com.v2ray.ang.util

import com.v2ray.ang.util.JsonUtil

/**
 * Serialization for locked-profile packages.
 *
 * The package is a text blob with a header/footer marker and a JSON-serialized list of
 * entries in between, so multi-line raw profiles (OpenVPN) survive a round trip. Each
 * entry carries the config text plus the lock conditions (expiry moment and data limit)
 * applied on the originating device, so importing re-applies both the profile and its
 * lock settings under a permanent lock. Import detects the markers; any surrounding text
 * outside the markers is imported normally with no lock applied.
 */
object LockedPackage {
    const val HEADER = "#V2RAYNG-LOCK-PACKAGE-BEGIN#"
    const val FOOTER = "#V2RAYNG-LOCK-PACKAGE-END#"

    data class LockedEntry(
        val content: String,
        val expiryEpochMinute: Long = 0L,
        val dataLimitBytes: Long = 0L,
    )

    data class Parsed(val entries: List<LockedEntry>, val remaining: String)

    fun encode(entries: List<LockedEntry>): String {
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine(HEADER)
            appendLine(JsonUtil.toJson(entries))
            appendLine(FOOTER)
        }
    }

    fun parse(text: String): Parsed {
        val inBlock = StringBuilder()
        val remaining = StringBuilder()
        var inside = false

        text.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed == HEADER || trimmed == FOOTER) {
                inside = trimmed == HEADER
            } else if (inside) {
                inBlock.appendLine(line)
            } else if (trimmed.isNotBlank()) {
                remaining.appendLine(line)
            }
        }

        return Parsed(entries = parseEntries(inBlock.toString()), remaining = remaining.toString())
    }

    /**
     * Reads a locked-package payload. Packages written by older releases stored a plain
     * array of config strings; current releases store an array of entry objects that also
     * carry the lock conditions. Both layouts parse here.
     */
    private fun parseEntries(json: String): List<LockedEntry> {
        JsonUtil.fromJsonSafe(json, Array<LockedEntry>::class.java)
            ?.filter { it.content.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toList() }
        return JsonUtil.fromJsonSafe(json, Array<String>::class.java)
            ?.filter { it.isNotBlank() }
            ?.map { LockedEntry(it) }
            ?: emptyList()
    }
}