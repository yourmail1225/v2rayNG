package com.v2ray.ang.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnTunConfigTest {

    @Test
    fun ipv4ConversionsRoundTrip() {
        assertEquals(0x0a080002L, ipv4ToLong("10.8.0.2"))
        assertEquals("10.8.0.2", longToIpv4(0x0a080002L))
        assertEquals(0xc0a80101L, ipv4ToLong("192.168.1.1"))
        assertEquals("192.168.1.1", longToIpv4(0xc0a80101L))
    }

    @Test
    fun prefixFromNetmaskComputesContiguousPrefix() {
        assertEquals(24, prefixFromNetmask("255.255.255.0"))
        assertEquals(16, prefixFromNetmask("255.255.0.0"))
        assertEquals(8, prefixFromNetmask("255.0.0.0"))
        assertEquals(32, prefixFromNetmask("255.255.255.255"))
        assertEquals(0, prefixFromNetmask("0.0.0.0"))
        // Non-contiguous masks fall back to a /32 rather than misrouting.
        assertEquals(32, prefixFromNetmask("255.255.0.255"))
    }

    @Test
    fun computeLocalPrefixDetectsPointToPoint() {
        assertEquals(30, computeLocalPrefix("10.8.0.2", "10.8.0.1", "net30"))
        assertEquals(31, computeLocalPrefix("10.8.0.2", "10.8.0.3", "p2p"))
        // Peer not on the same /30 or /31 boundary stays a host route.
        assertEquals(32, computeLocalPrefix("10.8.0.2", "10.8.1.1", "net30"))
        assertEquals(32, computeLocalPrefix("10.8.0.2", "10.8.0.5", "p2p"))
        assertEquals(32, computeLocalPrefix("10.8.0.2", "255.255.255.255", "net30"))
        // A real subnet mask is kept as-is.
        assertEquals(24, computeLocalPrefix("10.8.0.2", "255.255.255.0", "subnet"))
    }

    @Test
    fun shouldIncludeRouteAcceptsTunDeviceAndLocalGateways() {
        val localIP = "10.8.0.2/30"
        val remoteGW = "10.8.0.1"
        assertTrue(shouldIncludeRoute("tun0", "10.8.0.1", localIP, remoteGW))
        assertTrue(shouldIncludeRoute("vpnservice-tun", "10.8.0.3", localIP, remoteGW))
        assertTrue(shouldIncludeRoute("wlan0", "10.8.0.1", localIP, remoteGW))
        assertTrue(shouldIncludeRoute("wlan0", "255.255.255.255", localIP, remoteGW))
        assertTrue(shouldIncludeRoute("wlan0", "10.8.0.3", localIP, remoteGW))
        assertFalse(shouldIncludeRoute("wlan0", "192.168.1.1", localIP, remoteGW))
        assertFalse(shouldIncludeRoute(null, "10.9.0.1", localIP, remoteGW))
        assertFalse(shouldIncludeRoute("wlan0", "10.9.0.1", null, null))
    }

    @Test
    fun ownTunDeviceMatchesTunNames() {
        assertTrue(ownTunDevice("tun0"))
        assertTrue(ownTunDevice("vpnservice-tun"))
        assertTrue(ownTunDevice("(null)"))
        assertFalse(ownTunDevice("wlan0"))
        assertFalse(ownTunDevice(null))
    }

    @Test
    fun computeSelfNetworkRouteAddsOwnLinkOnly() {
        assertEquals("10.8.0.0/30", computeSelfNetworkRoute("10.8.0.2", 30))
        assertEquals("10.8.0.2/31", computeSelfNetworkRoute("10.8.0.2", 31))
        assertNull(computeSelfNetworkRoute("10.8.0.2", 32))
        assertNull(computeSelfNetworkRoute("0.0.0.0", 30))
    }

    @Test
    fun buildEnhancedConfigPrependsManagementAndStripsManagedOptions() {
        val socket = "/data/user/0/com.v2ray.ang/cache/mgmtsocket"
        val raw = """
            management 127.0.0.1 5555
            client
            dev tun
            remote vpn.example.com 443
            auth-user-pass /tmp/creds
            machine-readable-output
        """.trimIndent()
        val config = OpenVpnEngine.buildEnhancedConfig(raw, socket)
        assertTrue(config.contains("management $socket unix\n"))
        assertTrue(config.contains("management-client\n"))
        assertTrue(config.contains("management-hold\n"))
        assertTrue(config.startsWith("# v2rayNG managed OpenVPN 2.x config\n"))
        // Provider management options are removed to avoid a second listener.
        assertFalse(config.contains("127.0.0.1 5555"))
        assertFalse(config.contains("machine-readable-output"))
        // A file-backed auth-user-pass is downgraded to the interactive prompt.
        assertTrue(config.contains("auth-user-pass\n"))
        assertFalse(config.contains("/tmp/creds"))
        assertTrue(config.contains("remote vpn.example.com 443"))
    }

    @Test
    fun buildEnhancedConfigKeepsInlineCredentialBlock() {
        val config = OpenVpnEngine.buildEnhancedConfig(
            "client\nauth-user-pass\nmyuser\nmypassword\nremote host 443\n",
            "/mgmt/socket"
        )
        assertTrue(config.contains("auth-user-pass\nmyuser\nmypassword"))
    }

    @Test
    fun parseInlineCredentialsReadsAdjacentLines() {
        assertEquals("myuser" to "mypassword", OpenVpnEngine.parseInlineCredentials("auth-user-pass\nmyuser\nmypassword"))
        assertEquals("a" to "b", OpenVpnEngine.parseInlineCredentials("# c\nauth-user-pass\na\nb\n"))
        assertNull(OpenVpnEngine.parseInlineCredentials("auth-user-pass /file/storage\n"))
        assertNull(OpenVpnEngine.parseInlineCredentials("client\ndev tun\n"))
        assertNull(OpenVpnEngine.parseInlineCredentials(""))
    }
}