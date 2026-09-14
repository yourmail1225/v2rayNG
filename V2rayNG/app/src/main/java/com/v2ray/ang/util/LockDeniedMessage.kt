package com.v2ray.ang.util

import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.handler.AppLocaleManager

/** Localizes the lock-denied notice shown to the user. */
object LockDeniedMessage {
    fun resolve(context: Context, reason: LockEvaluator.DeniedReason): String {
        val localized = AppLocaleManager.localizedContext(context)
        return when (reason) {
            LockEvaluator.DeniedReason.EXPIRED ->
                localized.getString(R.string.lock_group_expired)

            LockEvaluator.DeniedReason.DATA_LIMIT_REACHED ->
                localized.getString(R.string.lock_group_data_exceeded)
        }
    }
}