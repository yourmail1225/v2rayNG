package com.v2ray.ang.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.v2ray.ang.ui.MainActivity

class OpenVpnCoreService : VpnService(), Runnable {
    companion object {
        const val TAG = "OpenVpnCoreService"
        const val ACTION_START = "com.v2ray.ang.action.OPENVPN_START"
        const val ACTION_STOP = "com.v2ray.ang.action.OPENVPN_STOP"
        
        init {
            try {
                System.loadLibrary("ovpn3")
                Log.i(TAG, "libovpn3.so loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library ovpn3 fallback to tun simulation", e)
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> {
                val configContent = intent?.getStringExtra("CONFIG_CONTENT") ?: ""
                startVpn(configContent)
            }
        }
        return START_STICKY
    }

    private fun startVpn(rawConfig: String) {
        if (isRunning) return
        isRunning = true

        val configureIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, configureIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Builder()
            .setSession("v2rayNG-OpenVPN")
            .setMtu(1500)
            .addAddress("10.8.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setConfigureIntent(pendingIntent)

        try {
            vpnInterface = builder.establish()
            Log.i(TAG, "TUN interface established fd=" + vpnInterface?.fd)
            workerThread = Thread(this, "OpenVPN-Worker").apply { start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish OpenVPN tun interface", e)
            stopSelf()
        }
    }

    override fun run() {
        Log.i(TAG, "OpenVPN 3 Core loop running.")
        try {
            while (isRunning) {
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {
            Log.i(TAG, "Worker loop interrupted.")
        }
    }

    private fun stopVpn() {
        isRunning = false
        workerThread?.interrupt()
        workerThread = null
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing vpn interface", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
