package com.v2ray.ang.dto.entities

data class GroupLockConfig(
    val enabled: Boolean = false,
    val expiryEpochDay: Long = 0L,
    val dataLimitBytes: Long = 0L,
    var usedBytes: Long = 0L,
)