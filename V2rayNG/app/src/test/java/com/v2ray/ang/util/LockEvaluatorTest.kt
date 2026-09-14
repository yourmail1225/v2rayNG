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
}