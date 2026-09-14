package com.v2ray.ang.util

import com.v2ray.ang.util.JsonUtil

/**
 * Serialization for locked-profile packages.
 *
 * The package is a text blob with a header/footer marker and a JSON-serialized list of
 * config texts in between, so multi-line raw profiles (OpenVPN) survive a round trip.
 * Import detects the markers; any surrounding text outside the markers is imported
 * normally with no lock applied.
 */
object LockedPackage {
    const val HEADER = "#V2RAYNG-LOCK-PACKAGE-BEGIN#"
    const val FOOTER = "#V2RAYNG-LOCK-PACKAGE-END#"

    data class LockedEntry(val content: String)

    data class Parsed(val entries: List<LockedEntry>, val remaining: String)

    fun encode(entries: List<LockedEntry>): String {
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine(HEADER)
            appendLine(JsonUtil.toJson(entries.map { it.content }))
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

        val entries = JsonUtil.fromJsonSafe(inBlock.toString(), Array<String>::class.java)
            ?.filter { it.isNotBlank() }
            ?.map { LockedEntry(it) }
            ?: emptyList()

        return Parsed(entries = entries, remaining = remaining.toString())
    }
}