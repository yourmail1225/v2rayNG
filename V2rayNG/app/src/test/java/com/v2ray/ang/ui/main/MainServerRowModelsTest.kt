package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.GroupLockConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
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

    @Test
    fun buildLockUsageWithPermanentLockUsesSharedCounter() {
        val aff = ServerAffiliationInfo(
            persistentLock = true,
            dataLimitBytes = 100,
            usedBytes = 5,
        )
        val usage = buildLockUsage(aff, sharedUsedBytes = 42)
        // Imported package profiles share one subscription-wide counter.
        assertEquals(42L, usage.usedBytes)
        assertEquals(100L, usage.dataLimitBytes)
    }

    @Test
    fun buildLockUsageWithoutSharedArgFallsBackToAffiliationCounter() {
        val aff = ServerAffiliationInfo(
            persistentLock = true,
            dataLimitBytes = 100,
            usedBytes = 7,
        )
        assertEquals(7L, buildLockUsage(aff).usedBytes)
    }

    @Test
    fun buildGroupLockUsageIsEmptyWhenNoGroupLock() {
        assertFalse(buildGroupLockUsage(null).isActive)
    }

    @Test
    fun buildGroupLockUsageIsEmptyWhenGroupLockDisabled() {
        val lock = GroupLockConfig(
            enabled = false,
            expiryEpochMinute = 200,
            dataLimitBytes = 100,
            usedBytes = 10,
        )
        assertFalse(buildGroupLockUsage(lock).isActive)
    }

    @Test
    fun buildGroupLockUsagePopulatesCombinedGroupCounter() {
        val lock = GroupLockConfig(
            enabled = true,
            expiryEpochMinute = 200,
            startEpochMinute = 100,
            dataLimitBytes = 100,
            usedBytes = 10,
        )
        val usage = buildGroupLockUsage(lock)
        // The whole-group shared counter, not a per-profile share.
        assertEquals(10L, usage.usedBytes)
        assertEquals(100L, usage.dataLimitBytes)
        assertEquals(200L, usage.expiryEpochMinute)
        assertEquals(100L, usage.startEpochMinute)
        assertTrue(usage.isActive)
        assertTrue(usage.nowEpochMinute > 0L)
    }

    @Test
    fun activeGroupLockMarksUnlockedProfilesLocked() {
        val row = buildServerRowUiModel(
            server = ServersCache(
                guid = "guid-a",
                profile = ProfileItem(configType = EConfigType.VMESS, remarks = "a"),
                locked = false,
            ),
            subscriptionRemarks = "",
            affiliation = null,
            groupLock = GroupLockConfig(
                enabled = true,
                dataLimitBytes = 100,
                usedBytes = 25,
            ),
        )
        assertTrue(row.locked)
        // With no profile lock of its own, the row renders the group-wide quota.
        assertEquals(25L, row.usage.usedBytes)
        assertEquals(100L, row.usage.dataLimitBytes)
        assertTrue(row.usage.isActive)
    }

    @Test
    fun profileLockTakesPrecedenceOverGroupLockUsage() {
        val row = buildServerRowUiModel(
            server = ServersCache(
                guid = "guid-a",
                profile = ProfileItem(configType = EConfigType.VMESS, remarks = "a"),
                locked = true,
            ),
            subscriptionRemarks = "",
            affiliation = ServerAffiliationInfo(
                locked = true,
                dataLimitBytes = 50,
                usedBytes = 5,
                expiryEpochMinute = 200,
                startEpochMinute = 100,
            ),
            groupLock = GroupLockConfig(
                enabled = true,
                dataLimitBytes = 100,
                usedBytes = 25,
            ),
        )
        assertTrue(row.locked)
        // The profile's own more-specific lock drives the card bars.
        assertEquals(5L, row.usage.usedBytes)
        assertEquals(50L, row.usage.dataLimitBytes)
    }

    @Test
    fun disabledGroupLockLeavesProfileUnlocked() {
        val row = buildServerRowUiModel(
            server = ServersCache(
                guid = "guid-a",
                profile = ProfileItem(configType = EConfigType.VMESS, remarks = "a"),
                locked = false,
            ),
            subscriptionRemarks = "g",
            affiliation = null,
            groupLock = GroupLockConfig(enabled = false),
        )
        assertFalse(row.locked)
        assertFalse(row.usage.isActive)
    }

    @Test
    fun permanentLockedProfileRendersSharedSubscriptionCounter() {
        val row = buildServerRowUiModel(
            server = ServersCache(
                guid = "guid-a",
                profile = ProfileItem(configType = EConfigType.VMESS, remarks = "a"),
                locked = true,
            ),
            subscriptionRemarks = "g",
            affiliation = ServerAffiliationInfo(
                persistentLock = true,
                dataLimitBytes = 100,
                usedBytes = 5,
                expiryEpochMinute = 200,
                startEpochMinute = 100,
            ),
            sharedLockedUsedBytes = 60,
        )
        assertTrue(row.locked)
        // The subscription's shared used bytes, not the profile's own counter.
        assertEquals(60L, row.usage.usedBytes)
        assertEquals(100L, row.usage.dataLimitBytes)
    }

    @Test
    fun ordinaryLockedProfileIgnoresSharedCounter() {
        val row = buildServerRowUiModel(
            server = ServersCache(
                guid = "guid-a",
                profile = ProfileItem(configType = EConfigType.VMESS, remarks = "a"),
                locked = true,
            ),
            subscriptionRemarks = "g",
            affiliation = ServerAffiliationInfo(
                locked = true,
                dataLimitBytes = 50,
                usedBytes = 5,
            ),
            sharedLockedUsedBytes = 999,
        )
        // Editable profile locks keep their own per-profile used counter.
        assertEquals(5L, row.usage.usedBytes)
        assertEquals(50L, row.usage.dataLimitBytes)
    }

    @Test
    fun remainingDaysIsZeroWithoutExpiry() {
        assertEquals(0L, remainingDays(expiryEpochMinute = 0L, nowEpochMinute = 100L))
    }

    @Test
    fun remainingDaysIsZeroAtAndAfterExpiry() {
        assertEquals(0L, remainingDays(expiryEpochMinute = 100L, nowEpochMinute = 100L))
        assertEquals(0L, remainingDays(expiryEpochMinute = 100L, nowEpochMinute = 200L))
    }

    @Test
    fun remainingDaysRoundsUpPartialDays() {
        assertEquals(1L, remainingDays(expiryEpochMinute = 100L, nowEpochMinute = 1L))
        assertEquals(1L, remainingDays(expiryEpochMinute = 101L, nowEpochMinute = 100L))
        assertEquals(1L, remainingDays(expiryEpochMinute = 1540L, nowEpochMinute = 100L))
        assertEquals(2L, remainingDays(expiryEpochMinute = 1541L, nowEpochMinute = 100L))
    }

    @Test
    fun remainingDaysIsExactForWholeDayWindows() {
        assertEquals(1L, remainingDays(expiryEpochMinute = 1540L, nowEpochMinute = 100L))
        assertEquals(2L, remainingDays(expiryEpochMinute = 2980L, nowEpochMinute = 100L))
    }
}