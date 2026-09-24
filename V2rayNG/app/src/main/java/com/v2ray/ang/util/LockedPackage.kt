package com.v2ray.ang.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
     * Reads a locked-package payload. Older releases stored a plain array of config strings;
     * current releases store an array of entry objects that also carry the lock conditions.
     * The layout is detected from parsed JSON without logging, because a block that is absent,
     * legacy-layout, or not JSON at all is the ordinary "pass it through as surrounding text"
     * case rather than a fault, and plain JVM unit tests have no android.util.Log to report to.
     */
    private fun parseEntries(json: String): List<LockedEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            parseEntries(JsonParser.parseString(json).asJsonArray)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseEntries(array: JsonArray): List<LockedEntry> =
        if (array.any { it.isJsonObject }) {
            array.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val obj = element.asJsonObject
                val content = obj.lockString("content")
                if (content.isBlank()) null else LockedEntry(
                    content,
                    expiryEpochMinute = obj.lockLong("expiryEpochMinute"),
                    dataLimitBytes = obj.lockLong("dataLimitBytes"),
                )
            }
        } else {
            array.mapNotNull { element ->
                val content = element.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (content.isBlank()) null else LockedEntry(content)
            }
        }

    private fun JsonObject.lockString(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.lockLong(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
}