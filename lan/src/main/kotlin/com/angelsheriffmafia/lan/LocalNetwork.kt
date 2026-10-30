package com.angelsheriffmafia.lan

import java.net.Inet4Address
import java.net.NetworkInterface

/** An address other phones on the same Wi-Fi or hotspot can reach this phone at. */
data class LocalAddress(val ip: String, val interfaceName: String)

object LocalNetwork {

    /**
     * This phone's local network addresses, best first: Wi-Fi and hotspot before anything else.
     * Mobile data and VPN interfaces are left out, since friends can't reach them.
     */
    fun addresses(): List<LocalAddress> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        return interfaces
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { nif ->
                nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { LocalAddress(it.hostAddress, nif.name) }
            }
            .filter { usable(it.interfaceName) }
            .sortedBy { rank(it.interfaceName) }
            .distinctBy { it.ip }
    }

    private val excluded = listOf("rmnet", "ccmni", "pdp", "clat", "v4-", "tun", "ppp", "dummy", "ipsec", "docker", "veth", "br-")

    internal fun usable(name: String) = excluded.none { name.startsWith(it) }

    /** Hotspot first (its address is the one friends' phones join), then Wi-Fi, then anything else. */
    internal fun rank(name: String) = when {
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 0
        name.startsWith("wlan") -> 1
        name.startsWith("eth") || name.startsWith("en") -> 2
        else -> 3
    }

    fun url(address: String, port: Int) = if (port == 80) "http://$address/" else "http://$address:$port/"
}
