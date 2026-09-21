package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainServerRowModelsTest {

    @Test
    fun dataUsageFractionIsNullWithoutLimit() {
        assertNull(dataUsageFraction(usedBytes = 100, dataLimitBytes = 0L))
    }

    @Test
    fun dataUsageFractionIsRatio() {
        assertEquals(0.5f, dataUsageFraction(usedBytes = 50, dataLimitBytes = 100)!!, 0.0001f)
    }

    @Test
    fun dataUsageFractionClampsAboveLimitToOne() {
        assertEquals(1f, dataUsageFraction(usedBytes = 200, dataLimitBytes = 100)!!, 0.0001f)
    }

    @Test
    fun dataUsageFractionIsZeroWhenNothingConsumed() {
        assertEquals(0f, dataUsageFraction(usedBytes = 0, dataLimitBytes = 100)!!, 0.0001f)
    }

    @Test
    fun timeRemainingFractionIsNullWithoutValidWindow() {
        assertNull(timeRemainingFraction(startEpochMinute = 0L, expiryEpochMinute = 100L, nowEpochMinute = 50L))
        assertNull(timeRemainingFraction(startEpochMinute = 100L, expiryEpochMinute = 100L, nowEpochMinute = 50L))
        assertNull(timeRemainingFraction(startEpochMinute = 100L, expiryEpochMinute = 50L, nowEpochMinute = 50L))
    }

    @Test
    fun timeRemainingFractionIsFullBeforeWindowStart() {
        assertEquals(1f, timeRemainingFraction(100L, 200L, 50L)!!, 0.0001f)
    }

    @Test
    fun timeRemainingFractionIsHalfwayThroughWindow() {
        assertEquals(0.5f, timeRemainingFraction(100L, 200L, 150L)!!, 0.0001f)
    }

    @Test
    fun timeRemainingFractionIsZeroAtAndAfterExpiry() {
        assertEquals(0f, timeRemainingFraction(100L, 200L, 200L)!!, 0.0001f)
        assertEquals(0f, timeRemainingFraction(100L, 200L, 250L)!!, 0.0001f)
    }

    @Test
    fun usageModelIsInactiveWithoutConstraints() {
        val usage = LockUsageUiModel()
        assertFalse(usage.hasDataLimit)
        assertFalse(usage.hasTimeLimit)
        assertFalse(usage.isActive)
    }

    @Test
    fun usageModelFlagsRespectDataAndTimeConstraints() {
        val usage = LockUsageUiModel(
            usedBytes = 10,
            dataLimitBytes = 100,
            expiryEpochMinute = 200,
            startEpochMinute = 100,
            nowEpochMinute = 150,
        )
        assertTrue(usage.hasDataLimit)
        assertTrue(usage.hasTimeLimit)
        assertTrue(usage.isActive)
    }

    @Test
    fun buildLockUsageIsEmptyWhenNoAffiliation() {
        assertFalse(buildLockUsage(null).isActive)
    }

    @Test
    fun buildLockUsageIsEmptyWhenLockDisabled() {
        val aff = ServerAffiliationInfo(
            locked = false,
            expiryEpochMinute = 200,
            dataLimitBytes = 100,
            usedBytes = 10,
        )
        assertFalse(buildLockUsage(aff).isActive)
    }

    @Test
    fun buildLockUsagePopulatesActiveLockSnapshot() {
        val aff = ServerAffiliationInfo(
            locked = true,
            expiryEpochMinute = 200,
            dataLimitBytes = 100,
            usedBytes = 10,
            startEpochMinute = 100,
        )
        val usage = buildLockUsage(aff)
        assertEquals(10L, usage.usedBytes)
        assertEquals(100L, usage.dataLimitBytes)
        assertEquals(200L, usage.expiryEpochMinute)
        assertEquals(100L, usage.startEpochMinute)
        assertTrue(usage.isActive)
        assertTrue(usage.nowEpochMinute > 0L)
    }

    @Test
    fun buildLockUsageAcceptsPermanentLock() {
        val aff = ServerAffiliationInfo(
            persistentLock = true,
            dataLimitBytes = 100,
        )
        assertTrue(buildLockUsage(aff).hasDataLimit)
    }
}