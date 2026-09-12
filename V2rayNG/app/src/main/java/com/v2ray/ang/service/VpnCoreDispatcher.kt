package com.v2ray.ang.service

import android.content.Context
import android.content.Intent
import android.util.Log

object VpnCoreDispatcher {
    private const val TAG = "VpnCoreDispatcher"

    fun startService(context: Context, serverType: String, configData: String) {
        if (serverType.equals("openvpn", ignoreCase = true)) {
            Log.i(TAG, "Routing to OpenVpnCoreService (OpenVPN 3 Native C++)")
            val intent = Intent(context, OpenVpnCoreService::class.java).apply {
                putExtra("CONFIG_CONTENT", configData)
            }
            context.startForegroundService(intent)
        } else {
            Log.i(TAG, "Routing to AndroidLibXrayLite (Xray-core)")
            V2RayServiceManager.startV2Ray(context)
        }
    }

    fun stopService(context: Context) {
        val openvpnIntent = Intent(context, OpenVpnCoreService::class.java).apply {
            action = OpenVpnCoreService.ACTION_STOP
        }
        context.startService(openvpnIntent)
        V2RayServiceManager.stopV2ray(context)
    }
}
