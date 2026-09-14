package com.v2ray.ang.util

import com.v2ray.ang.dto.entities.GroupLockConfig
import java.text.ParsePosition
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
    private const val MINUTE_MILLIS = 60_000L
    private const val DAY_MINUTES = 1_440L

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
     * Maps a timestamp to the minute-of-epoch for its calendar date and time in [zone].
     * Unlike epoch seconds, the value is expressed in local wall-clock minutes, so it
     * round-trips through [formatEpochMinute] without any epoch-day timezone jitter.
     */
    fun todayEpochMinute(
        zone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return (nowMillis + zone.getOffset(nowMillis)) / MINUTE_MILLIS
    }

    /**
     * Parses `yyyy-MM-dd HH:mm` into a minute-of-epoch, or a bare `yyyy-MM-dd` into the
     * minute at the end of that day (allowing the whole expiry day, matching the legacy
     * date-only semantics). Returns 0 for blank or invalid text.
     */
    fun parseEpochMinute(text: String, zone: TimeZone = TimeZone.getDefault()): Long {
        if (text.isBlank()) return 0L
        val trimmed = text.trim()
        parseDateTime(trimmed, zone)?.let { return it }
        parseDateOnly(trimmed, zone)?.let { return it }
        return 0L
    }

    /**
     * Formats a minute-of-epoch as `yyyy-MM-dd HH:mm`, or an empty string for 0.
     */
    fun formatEpochMinute(epochMinute: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (epochMinute == 0L) return ""
        val millis = epochMinute * MINUTE_MILLIS - zone.getOffset(epochMinute * MINUTE_MILLIS)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = zone
            isLenient = false
        }.format(Date(millis))
    }

    private fun parseDateTime(text: String, zone: TimeZone): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = zone
                isLenient = false
            }
            val pos = ParsePosition(0)
            val date = sdf.parse(text, pos) ?: return null
            // DateFormat.parse ignores trailing text; require the whole input.
            if (pos.index != text.length) return null
            // Reject out-of-range fields like "25:00" that some JDKs silently normalize.
            if (sdf.format(date) != text) return null
            (date.time + zone.getOffset(date.time)) / MINUTE_MILLIS
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDateOnly(text: String, zone: TimeZone): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = zone
                isLenient = false
            }
            val pos = ParsePosition(0)
            val date = sdf.parse(text, pos) ?: return null
            // DateFormat.parse ignores trailing text; require the whole input.
            if (pos.index != text.length) return null
            val day = (date.time + zone.getOffset(date.time)) / DAY_MILLIS
            (day + 1) * DAY_MINUTES
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evaluates whether a group lock currently blocks a connection.
     *
     * @param lock The group lock configuration, or null when the group has no lock.
     * @param todayEpochDay The current date as an epoch day; injected so callers keep
     *                      control of the clock. Only used by legacy date-only locks.
     * @param nowEpochMinute The current wall-clock minute; used when a lock has
     *                       minute-granularity expiry.
     * @param currentUsedBytes The data already consumed by the group.
     */
    fun evaluate(
        lock: GroupLockConfig?,
        todayEpochDay: Long = todayEpochDay(),
        nowEpochMinute: Long = todayEpochMinute(),
        currentUsedBytes: Long = lock?.usedBytes ?: 0L
    ): Decision {
        if (lock == null || !lock.enabled) return Decision.Allow
        if (lock.expiryEpochMinute != 0L) {
            if (nowEpochMinute >= lock.expiryEpochMinute) {
                return Decision.Denied(DeniedReason.EXPIRED)
            }
        } else if (lock.expiryEpochDay != 0L && todayEpochDay > lock.expiryEpochDay) {
            return Decision.Denied(DeniedReason.EXPIRED)
        }
        if (lock.dataLimitBytes != 0L && currentUsedBytes >= lock.dataLimitBytes) {
            return Decision.Denied(DeniedReason.DATA_LIMIT_REACHED)
        }
        return Decision.Allow
    }
}