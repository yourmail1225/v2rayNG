package com.v2ray.ang.util

import com.v2ray.ang.dto.entities.GroupLockConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LockEvaluatorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun nullLockIsAlwaysAllowed() {
        assertTrue(LockEvaluator.evaluate(null, todayEpochDay = 500) is LockEvaluator.Decision.Allow)
    }

    @Test
    fun disabledLockIsAllowed() {
        val lock = GroupLockConfig(enabled = false, expiryEpochDay = 1, dataLimitBytes = 1)
        assertTrue(LockEvaluator.evaluate(lock, todayEpochDay = 999) is LockEvaluator.Decision.Allow)
    }

    @Test
    fun expiredLockIsDenied() {
        val lock = GroupLockConfig(enabled = true, expiryEpochDay = 10)
        val decision = LockEvaluator.evaluate(lock, todayEpochDay = 11)
        assertTrue(decision is LockEvaluator.Decision.Denied)
        assertEquals(LockEvaluator.DeniedReason.EXPIRED, (decision as LockEvaluator.Decision.Denied).reason)
    }

    @Test
    fun lockExpiresTheDayAfterTheExpiryDay() {
        val lock = GroupLockConfig(enabled = true, expiryEpochDay = 10)
        assertTrue(LockEvaluator.evaluate(lock, todayEpochDay = 10) is LockEvaluator.Decision.Allow)
    }

    @Test
    fun dataLimitReachedIsDenied() {
        val lock = GroupLockConfig(enabled = true, dataLimitBytes = 100)
        val decision = LockEvaluator.evaluate(lock, todayEpochDay = 0, currentUsedBytes = 100)
        assertTrue(decision is LockEvaluator.Decision.Denied)
        assertEquals(
            LockEvaluator.DeniedReason.DATA_LIMIT_REACHED,
            (decision as LockEvaluator.Decision.Denied).reason
        )
    }

    @Test
    fun dataLimitBelowThresholdIsAllowed() {
        val lock = GroupLockConfig(enabled = true, dataLimitBytes = 100)
        assertTrue(
            LockEvaluator.evaluate(lock, todayEpochDay = 0, currentUsedBytes = 99) is LockEvaluator.Decision.Allow
        )
    }

    @Test
    fun defaultZeroLimitDoesNotDeny() {
        val lock = GroupLockConfig(enabled = true)
        assertTrue(LockEvaluator.evaluate(lock, todayEpochDay = 10_000, currentUsedBytes = 10_000) is LockEvaluator.Decision.Allow)
    }

    @Test
    fun dateHelpersRoundTripViaUtc() {
        val epochDay = LockEvaluator.parseEpochDay("2025-06-01", utc)
        assertEquals(epochDay, LockEvaluator.todayEpochDay(utc, nowMillis = epochDay * 86_400_000L))
        assertEquals("2025-06-01", LockEvaluator.formatEpochDay(epochDay, utc))
    }

    @Test
    fun parseEpochDayBlankOrInvalidReturnsZero() {
        assertEquals(0L, LockEvaluator.parseEpochDay("", utc))
        assertEquals(0L, LockEvaluator.parseEpochDay("not-a-date", utc))
        assertEquals(0L, LockEvaluator.parseEpochDay("2025/06/01", utc))
    }

    @Test
    fun formatEpochDayZeroIsEmptyString() {
        assertEquals("", LockEvaluator.formatEpochDay(0, utc))
    }

    @Test
    fun todayEpochDayUsesUtcWhenAsked() {
        // Exactly one day since the Unix epoch in UTC.
        val day = LockEvaluator.todayEpochDay(utc, nowMillis = 86_400_000L)
        assertEquals(1L, day)
    }

    @Test
    fun todayEpochMinuteUsesUtcWhenAsked() {
        // 2025-06-01 10:30 UTC in wall-clock minutes.
        val minute = LockEvaluator.todayEpochMinute(utc, nowMillis = 29_146_230L * 60_000L)
        assertEquals(29_146_230L, minute)
    }

    @Test
    fun parseEpochMinuteRoundTripsDateTime() {
        val minute = LockEvaluator.parseEpochMinute("2025-06-01 10:30", utc)
        assertEquals(29_146_230L, minute)
        assertEquals("2025-06-01 10:30", LockEvaluator.formatEpochMinute(minute, utc))
    }

    @Test
    fun parseEpochMinuteDateOnlyBecomesEndOfDay() {
        // A bare date keeps the legacy allow-whole-expiry-day semantics: expiry at
        // the minute after 23:59 of that day.
        val minute = LockEvaluator.parseEpochMinute("2025-06-01", utc)
        assertEquals("2025-06-02 00:00", LockEvaluator.formatEpochMinute(minute, utc))
    }

    @Test
    fun parseEpochMinuteBlankOrInvalidReturnsZero() {
        assertEquals(0L, LockEvaluator.parseEpochMinute("", utc))
        assertEquals(0L, LockEvaluator.parseEpochMinute("not-a-date", utc))
        assertEquals(0L, LockEvaluator.parseEpochMinute("2025-06-01 25:00", utc))
        assertEquals(0L, LockEvaluator.parseEpochMinute("2025/06/01 10:30", utc))
    }

    @Test
    fun formatEpochMinuteZeroIsEmptyString() {
        assertEquals("", LockEvaluator.formatEpochMinute(0, utc))
    }

    @Test
    fun minuteExpiryDeniesOnOrAfterExpiryMinute() {
        val lock = GroupLockConfig(enabled = true, expiryEpochMinute = 29_146_230L)
        assertTrue(
            LockEvaluator.evaluate(lock, nowEpochMinute = 29_146_229L) is LockEvaluator.Decision.Allow
        )
        val denied = LockEvaluator.evaluate(lock, nowEpochMinute = 29_146_230L)
        assertTrue(denied is LockEvaluator.Decision.Denied)
        assertEquals(LockEvaluator.DeniedReason.EXPIRED, (denied as LockEvaluator.Decision.Denied).reason)
        assertTrue(
            LockEvaluator.evaluate(lock, nowEpochMinute = 29_146_231L) is LockEvaluator.Decision.Denied
        )
    }

    @Test
    fun legacyDayExpiryStillAppliesWhenNoMinuteSet() {
        val lock = GroupLockConfig(enabled = true, expiryEpochDay = 10)
        assertTrue(
            LockEvaluator.evaluate(lock, todayEpochDay = 11, nowEpochMinute = 500_000) is LockEvaluator.Decision.Denied
        )
    }

    @Test
    fun minuteExpiryTakesPrecedenceOverLegacyDay() {
        val lock = GroupLockConfig(enabled = true, expiryEpochDay = 10, expiryEpochMinute = 29_146_230L)
        // Minute unexpired even though the legacy day already elapsed.
        assertTrue(
            LockEvaluator.evaluate(lock, todayEpochDay = 11, nowEpochMinute = 29_146_229L) is LockEvaluator.Decision.Allow
        )
    }
}