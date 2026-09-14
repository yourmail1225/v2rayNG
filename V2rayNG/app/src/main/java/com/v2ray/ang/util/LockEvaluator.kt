package com.v2ray.ang.util

import com.v2ray.ang.dto.entities.GroupLockConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure decision logic for subscription-group locks, kept JVM-testable. Date math
 * avoids java.time so it stays valid on minSdk 24 devices.
 */
object LockEvaluator {
    private const val DAY_MILLIS = 86_400_000L

    enum class DeniedReason {
        EXPIRED,
        DATA_LIMIT_REACHED
    }

    sealed class Decision {
        data object Allow : Decision()
        data class Denied(val reason: DeniedReason) : Decision()
    }

    /**
     * Maps a timestamp to the [epoch day](https://en.wikipedia.org/wiki/Julian_day)
     * of its calendar date in [zone].
     */
    fun todayEpochDay(
        zone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return (nowMillis + zone.getOffset(nowMillis)) / DAY_MILLIS
    }

    /**
     * Parses a `yyyy-MM-dd` date into an epoch day, or 0 when the text is invalid.
     */
    fun parseEpochDay(text: String, zone: TimeZone = TimeZone.getDefault()): Long {
        if (text.isBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = zone
                isLenient = false
            }
            val date = sdf.parse(text) ?: return 0L
            (date.time + zone.getOffset(date.time)) / DAY_MILLIS
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Formats an epoch day as a `yyyy-MM-dd` string, or an empty string for 0.
     */
    fun formatEpochDay(epochDay: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (epochDay == 0L) return ""
        val millis = epochDay * DAY_MILLIS - zone.getOffset(epochDay * DAY_MILLIS)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = zone
            isLenient = false
        }.format(Date(millis))
    }

    /**
     * Evaluates whether a group lock currently blocks a connection.
     *
     * @param lock The group lock configuration, or null when the group has no lock.
     * @param todayEpochDay The current date as an epoch day; injected so callers keep
     *                      control of the clock.
     * @param currentUsedBytes The data already consumed by the group.
     */
    fun evaluate(
        lock: GroupLockConfig?,
        todayEpochDay: Long = todayEpochDay(),
        currentUsedBytes: Long = lock?.usedBytes ?: 0L
    ): Decision {
        if (lock == null || !lock.enabled) return Decision.Allow
        if (lock.expiryEpochDay != 0L && todayEpochDay > lock.expiryEpochDay) {
            return Decision.Denied(DeniedReason.EXPIRED)
        }
        if (lock.dataLimitBytes != 0L && currentUsedBytes >= lock.dataLimitBytes) {
            return Decision.Denied(DeniedReason.DATA_LIMIT_REACHED)
        }
        return Decision.Allow
    }
}