package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedPackageTest {

    @Test
    fun encodeEmptyListReturnsEmptyString() {
        assertEquals("", LockedPackage.encode(emptyList()))
    }

    @Test
    fun parseEmptyStringReturnsEmptyResult() {
        val result = LockedPackage.parse("")
        assertEquals(0, result.entries.size)
        assertEquals("", result.remaining.trim())
    }

    @Test
    fun roundTripSingleSimpleEntry() {
        val text = LockedPackage.encode(listOf(LockedPackage.LockedEntry("vmess://AAAA")))
        val parsed = LockedPackage.parse(text)

        assertEquals(1, parsed.entries.size)
        assertEquals("vmess://AAAA", parsed.entries[0].content)
        assertEquals("", parsed.remaining.trim())
    }

    @Test
    fun roundTripMultipleEntries() {
        val entries = listOf(
            LockedPackage.LockedEntry("first"),
            LockedPackage.LockedEntry("second")
        )
        val text = LockedPackage.encode(entries)
        val parsed = LockedPackage.parse(text)

        assertEquals(2, parsed.entries.size)
        assertEquals("first", parsed.entries[0].content)
        assertEquals("second", parsed.entries[1].content)
        assertEquals("", parsed.remaining.trim())
    }

    @Test
    fun multilineContentSurvivesRoundTrip() {
        val payload = "line1\nline2\nline3"
        val parsed = LockedPackage.parse(LockedPackage.encode(listOf(LockedPackage.LockedEntry(payload))))

        assertEquals(1, parsed.entries.size)
        assertEquals(payload, parsed.entries[0].content)
    }

    @Test
    fun surroundingTextIsCapturedAsRemaining() {
        val text = "vmess://prefix\n" +
            LockedPackage.HEADER + "\n" +
            """["locked-content"]""" + "\n" +
            LockedPackage.FOOTER + "\n" +
            "ss://suffix"

        val parsed = LockedPackage.parse(text)

        assertEquals(1, parsed.entries.size)
        assertEquals("locked-content", parsed.entries[0].content)
        val remaining = parsed.remaining.trim()
        assertTrue(remaining.contains("vmess://prefix"))
        assertTrue(remaining.contains("ss://suffix"))
        assertTrue(LockedPackage.HEADER !in remaining)
        assertTrue(LockedPackage.FOOTER !in remaining)
    }

    @Test
    fun parseEmptyJsonBlockYieldsNoEntries() {
        val text = LockedPackage.HEADER + "\n" + LockedPackage.FOOTER
        val parsed = LockedPackage.parse(text)

        assertEquals(0, parsed.entries.size)
        assertEquals("", parsed.remaining.trim())
    }

    @Test
    fun markerCaseOrWhitespaceIsNotMatched() {
        val text = "${LockedPackage.HEADER.lowercase()}\n[""a""]\n${LockedPackage.FOOTER.lowercase()}"
        val parsed = LockedPackage.parse(text)

        assertEquals(0, parsed.entries.size)
        assertTrue(parsed.remaining.contains("a"))
    }

    @Test
    fun footerWithoutHeaderTreatsPayloadAsRemaining() {
        val text = LockedPackage.FOOTER + "\nss://payload"
        val parsed = LockedPackage.parse(text)

        assertEquals(0, parsed.entries.size)
        assertTrue(parsed.remaining.contains("ss://payload"))
    }
}