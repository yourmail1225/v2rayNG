package com.v2ray.ang.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import java.io.File

class OpenVpnCoreService : VpnService(), OpenVpnCoreBridge {
    companion object {
        const val TAG = "OpenVpnCoreService"
        const val ACTION_START = "com.v2ray.ang.action.OPENVPN_START"
        const val ACTION_STOP = "com.v2ray.ang.action.OPENVPN_STOP"
        const val EXTRA_CONFIG = "CONFIG_CONTENT"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    @Volatile
    private var isRunning = false
    @Volatile
    private var savedConfig = ""
    @Volatile
    private var tunConfig = OpenVpnTunConfig()
    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null
    private var managementThread: OpenVpnManagementThread? = null
    private var managementThreadHandle: Thread? = null
    private var engine: OpenVpnEngine? = null
    private var lastTunCfg = OpenVpnTunConfig()
    private var lastNotificationTime = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Entering foreground before reading configuration or opening the tunnel
        // is required because this service is launched via startForegroundService().
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.OPENVPN,
            getString(R.string.app_name),
            getString(R.string.openvpn_notification_starting)
        )

        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }

            else -> {
                // Null intent or the VpnService.SERVICE_INTERFACE action is an
                // OS-driven restart of an always-on VPN. Reuse the config from a
                // previous start command in this process; CoreVpnService
                // re-dispatches with fresh config when this process was re-created.
                val configContent = intent?.getStringExtra(EXTRA_CONFIG) ?: savedConfig
                if (configContent.isEmpty()) {
                    LogUtil.w(TAG, "No OpenVPN config available on start command")
                    NotificationHelper.stopForeground(this)
                    stopSelf()
                    return START_NOT_STICKY
                }
                return if (startVpn(configContent)) START_STICKY else START_NOT_STICKY
            }
        }
    }

    private fun startVpn(rawConfig: String): Boolean {
        synchronized(lock) {
            if (isRunning) {
                LogUtil.i(TAG, "OpenVPN already running, ignoring duplicate start command")
                return false
            }
            savedConfig = rawConfig
            tunConfig = OpenVpnTunConfig()
            lastTunCfg = OpenVpnTunConfig()

            val socketPath = File(cacheDir, "mgmtsocket").absolutePath
            val mgmt = OpenVpnManagementThread(this, socketPath)
            // The bind must be ready before the openvpn process dials back.
            if (!mgmt.openManagementInterface()) {
                LogUtil.e(TAG, "Failed to create the OpenVPN management socket")
                NotificationHelper.stopForeground(this)
                stopSelf()
                return false
            }
            val eng = OpenVpnEngine(
                context = this,
                socketPath = socketPath,
                rawConfig = rawConfig,
                onLog = { line -> onProcessLogLine(line) },
                onExit = { onEngineExited() }
            )
            // Mark running before starting the process so an immediate process
            // exit still routes through the cleanup path.
            isRunning = true
            if (!eng.start()) {
                isRunning = false
                mgmt.closeManagementInterface()
                NotificationHelper.stopForeground(this)
                stopSelf()
                return false
            }
            engine = eng
            managementThread = mgmt
            managementThreadHandle = Thread(mgmt, "OpenVPN-Management").apply {
                isDaemon = false
                start()
            }

            updateNotification(getString(R.string.openvpn_notification_connecting))
            return true
        }
    }

    private fun onEngineExited() {
        if (!isRunning) {
            return
        }
        LogUtil.i(TAG, "OpenVPN process exited")
        mainHandler.post {
            if (isRunning) {
                stopVpn()
            }
        }
    }

    private fun onProcessLogLine(line: String) {
        if (line.contains("Initialization Sequence Completed")) {
            updateNotification(getString(R.string.openvpn_notification_connected))
        }
        if (line.startsWith("MANAGEMENT: ")) {
            return
        }
        LogUtil.i("OpenVPN", line)
    }

    private fun updateNotification(content: String) {
        mainHandler.post {
            if (isRunning) {
                runCatching {
                    NotificationHelper.updateNotification(
                        NotificationChannelType.OPENVPN,
                        this@OpenVpnCoreService,
                        getString(R.string.app_name),
                        content
                    )
                }
            }
        }
    }

    private fun stopVpn() {
        synchronized(lock) {
            val mgmt = managementThread
            val mgmtHandle = managementThreadHandle
            val eng = engine
            managementThread = null
            managementThreadHandle = null
            engine = null
            isRunning = false

            mgmt?.managmentCommand(OpenVpnEngine.SIGTERM)
            mgmt?.closeManagementInterface()
            eng?.stop()
            try {
                mgmtHandle?.join(3000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error closing VPN interface", e)
            }
            vpnInterface = null
            tunConfig = OpenVpnTunConfig()
            NotificationHelper.stopForeground(this)
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun addDNS(dns: String) {
        synchronized(lock) {
            tunConfig.mDns.add(dns)
        }
    }

    override fun addSearchDomain(domain: String) {
        synchronized(lock) {
            tunConfig.mSearchDomains.add(domain)
        }
    }

    override fun addHttpProxy(host: String, port: Int) {
        LogUtil.i(TAG, "OpenVPN provided HTTP proxy $host:$port")
    }

    override fun addRoute(dest: String, mask: String, gateway: String, device: String?) {
        synchronized(lock) {
            val prefix = prefixFromNetmask(mask)
            val include = shouldIncludeRoute(device, gateway, tunConfig.mLocalIP, tunConfig.mRemoteGW)
            if (!include) {
                LogUtil.i(TAG, "Ignoring non-tun route $dest/$prefix via $gateway")
                return
            }
            val network = normaliseToNetwork(ipv4ToLong(dest), prefix)
            if (network == 0L && prefix != 0) {
                LogUtil.w(TAG, "Ignoring invalid route $dest/$prefix")
                return
            }
            val entry = "${longToIpv4(network)}/$prefix"
            if (network != 0L || prefix == 0) {
                if (!tunConfig.mRoutes.contains(entry)) {
                    tunConfig.mRoutes.add(entry)
                }
            }
        }
    }

    override fun addRoutev6(networkCidr: String, device: String?) {
        synchronized(lock) {
            if (ownTunDevice(device) && !tunConfig.mRoutesv6.contains(networkCidr)) {
                tunConfig.mRoutesv6.add(networkCidr)
            }
        }
    }

    override fun setMtu(mtu: Int) {
        synchronized(lock) {
            tunConfig.mMtu = mtu
        }
    }

    override fun setLocalIP(local: String, remote: String, mtu: Int, topology: String) {
        synchronized(lock) {
            tunConfig.mMtu = mtu
            tunConfig.mRemoteGW = null
            val maskPrefix = prefixFromNetmask(remote)
            val prefix = computeLocalPrefix(local, remote, topology)
            if ((topology == "p2p" && maskPrefix < 32) || (topology == "net30" && maskPrefix < 30)) {
                LogUtil.w(TAG, "IP $local with peer $remote looks like a subnet for topology $topology")
            }
            tunConfig.mLocalIP = "$local/$prefix"
            tunConfig.mRemoteGW = remote
            // Android does not route to the tun's own network, add it explicitly.
            val ownRoute = computeSelfNetworkRoute(local, prefix)
            if (ownRoute != null && !tunConfig.mRoutes.contains(ownRoute)) {
                tunConfig.mRoutes.add(ownRoute)
            }
        }
    }

    override fun setLocalIPv6(addrCidr: String) {
        synchronized(lock) {
            tunConfig.mLocalIPv6 = addrCidr
        }
    }

    override fun openTun(): ParcelFileDescriptor? {
        LogUtil.i(TAG, "OpenVPN requested the tun device")
        return synchronized(lock) {
            val tc = tunConfig
            if (tc.mLocalIP == null && tc.mLocalIPv6 == null) {
                LogUtil.e(TAG, "OpenVPN provided no interface address, tun cannot be opened")
                null
            } else {
                openTunWithConfig(tc)
            }
        }
    }

    private fun openTunWithConfig(tc: OpenVpnTunConfig): ParcelFileDescriptor? {
        val builder = Builder().setSession("v2rayNG-OpenVPN").setMtu(tc.mMtu)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                builder.setConfigureIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        launchIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            val ip = tc.mLocalIP
            if (ip != null) {
                val idx = ip.indexOf('/')
                builder.addAddress(ip.substring(0, idx), ip.substring(idx + 1).toInt())
            }
            val ip6 = tc.mLocalIPv6
            if (ip6 != null) {
                val idx = ip6.indexOf('/')
                builder.addAddress(ip6.substring(0, idx), ip6.substring(idx + 1).toInt())
            }
            for (dns in tc.mDns) {
                runCatching { builder.addDnsServer(dns) }.onFailure { e ->
                    LogUtil.e(TAG, "DNS server rejected: $dns", e)
                }
            }
            for (search in tc.mSearchDomains) {
                runCatching { builder.addSearchDomain(search) }.onFailure { e ->
                    LogUtil.e(TAG, "Search domain rejected: $search", e)
                }
            }
            for (route in tc.mRoutes) {
                val idx = route.lastIndexOf('/')
                runCatching { builder.addRoute(route.substring(0, idx), route.substring(idx + 1).toInt()) }
                    .onFailure { e ->
                        LogUtil.e(TAG, "Route rejected: $route", e)
                    }
            }
            for (route6 in tc.mRoutesv6) {
                val idx = route6.lastIndexOf('/')
                runCatching { builder.addRoute(route6.substring(0, idx), route6.substring(idx + 1).toInt()) }
                    .onFailure { e ->
                        LogUtil.e(TAG, "Route rejected: $route6", e)
                    }
            }
            if (tc.mRoutes.isEmpty() && tc.mRoutesv6.isEmpty()) {
                builder.addRoute("0.0.0.0", 0)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to build tun interface", e)
            return null
        }

        lastTunCfg = tc
        // Close a previous interface before establishing a replacement so the
        // framework does not reject the second tun during a reconnect.
        vpnInterface?.close()
        vpnInterface = null
        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish tun interface (VPN permission was not granted?)", e)
            null
        }
        if (pfd == null) {
            LogUtil.e(TAG, "establish() returned null")
            return null
        }
        vpnInterface = pfd
        // The accumulation is complete; reset it so a future connection starts
        // from an empty set instead of reusing stale DNS/routes.
        tunConfig = OpenVpnTunConfig()
        return pfd
    }

    override fun protect(fd: Int): Boolean = super.protect(fd)

    override fun isTunConfigSame(): String {
        synchronized(lock) {
            return if (tunConfig.canonicalString() == lastTunCfg.canonicalString()) {
                "NOACTION"
            } else {
                "OPEN_BEFORE_CLOSE"
            }
        }
    }

    override fun reportState(state: String, detail: String) {
        if (!isRunning) {
            return
        }
        LogUtil.i(TAG, "OpenVPN state: $state $detail")
        val content = when (state) {
            "CONNECTED" -> getString(R.string.openvpn_notification_connected)
            "RECONNECTING" -> getString(R.string.openvpn_notification_reconnecting)
            "AUTH", "GET_CONFIG" -> getString(R.string.openvpn_notification_authenticating)
            "WAIT", "CONNECTING", "TCP_CONNECT", "RESOLVE" -> getString(R.string.openvpn_notification_connecting)
            "ASSIGN_IP", "ADD_ROUTES" -> getString(R.string.openvpn_notification_configuring)
            else -> getString(R.string.openvpn_notification_connecting)
        }
        updateNotification(content)
    }

    override fun reportByteCount(bytesIn: Long, bytesOut: Long) {
        val now = System.currentTimeMillis()
        // Only refresh the notification once per second to limit binder traffic.
        if (now - lastNotificationTime < 1000) {
            return
        }
        lastNotificationTime = now
        updateNotification("${formatBytes(bytesIn)} \u2193  ${formatBytes(bytesOut)} \u2191")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }

    override fun reportLog(level: String, msg: String) {
        when (level) {
            "E", "F" -> LogUtil.e(TAG, msg)
            "W" -> LogUtil.w(TAG, msg)
            "D" -> LogUtil.i(TAG, msg)
            else -> LogUtil.i(TAG, msg)
        }
    }

    override fun credentialsFor(needed: String): Pair<String, String>? {
        val eng = engine ?: return null
        if (needed != "Auth") {
            return null
        }
        val user = eng.username ?: return null
        val pass = eng.password ?: return null
        return user to pass
    }
}