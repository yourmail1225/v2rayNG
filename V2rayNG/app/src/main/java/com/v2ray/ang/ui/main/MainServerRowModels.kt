package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LockEvaluator

internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
    val locked: Boolean,
    val usage: LockUsageUiModel = LockUsageUiModel(),
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

/**
 * Snapshot of a locked profile's quota for the two card progress bars: the
 * consumed-data share and the remaining lock window. Values are captured when the
 * row is built, so every bar in a frame derives from the same [nowEpochMinute].
 */
internal data class LockUsageUiModel(
    val usedBytes: Long = 0L,
    val dataLimitBytes: Long = 0L,
    val expiryEpochMinute: Long = 0L,
    val startEpochMinute: Long = 0L,
    val nowEpochMinute: Long = 0L,
) {
    val hasDataLimit: Boolean get() = dataLimitBytes > 0L
    val hasTimeLimit: Boolean
        get() = expiryEpochMinute > 0L && startEpochMinute > 0L && expiryEpochMinute > startEpochMinute

    /** Whether either quota bar has a limit to render against. */
    val isActive: Boolean get() = hasDataLimit || hasTimeLimit
}

/**
 * Consumed share of a data limit, or null when there is no limit to fill against.
 */
internal fun dataUsageFraction(usedBytes: Long, dataLimitBytes: Long): Float? =
    if (dataLimitBytes > 0L) (usedBytes.toFloat() / dataLimitBytes.toFloat()).coerceIn(0f, 1f) else null

/**
 * Remaining share of the lock window [startEpochMinute, expiryEpochMinute] seen at
 * [nowEpochMinute], or null when no valid window exists.
 */
internal fun timeRemainingFraction(
    startEpochMinute: Long,
    expiryEpochMinute: Long,
    nowEpochMinute: Long,
): Float? {
    if (startEpochMinute <= 0L || expiryEpochMinute <= startEpochMinute) return null
    val total = expiryEpochMinute - startEpochMinute
    val remaining = (expiryEpochMinute - nowEpochMinute).coerceIn(0L, total)
    return remaining.toFloat() / total.toFloat()
}

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
    affiliation: ServerAffiliationInfo?,
): ServerRowUiModel {
    val profile = server.profile
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        locked = server.locked,
        usage = buildLockUsage(affiliation),
    )
}

internal fun buildLockUsage(affiliation: ServerAffiliationInfo?): LockUsageUiModel {
    val aff = affiliation ?: return LockUsageUiModel()
    // Only an active profile lock enforces a quota; a disabled lock's leftover
    // expiry and data-limit values are not shown as if they still applied.
    if (!aff.locked && !aff.persistentLock) return LockUsageUiModel()
    return LockUsageUiModel(
        usedBytes = aff.usedBytes,
        dataLimitBytes = aff.dataLimitBytes,
        expiryEpochMinute = aff.expiryEpochMinute,
        startEpochMinute = aff.startEpochMinute,
        nowEpochMinute = LockEvaluator.todayEpochMinute(),
    )
}

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}
