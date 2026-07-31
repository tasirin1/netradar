package com.tasirin.network.radar.util

import com.tasirin.network.radar.model.NetworkInterfaceInfo
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NetworkUtils {

    @Volatile
    var selectedInterfaceName: String = ""

    fun resolveTarget(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val baseIp = parts[0]
            val prefix = parts[1].toIntOrNull() ?: 24
            return expandCidr(baseIp, prefix)
        }
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-")
            val baseParts = parts[0].trim().split(".")
            if (baseParts.size == 4) {
                val start = baseParts[3].toIntOrNull() ?: return listOf(trimmed)
                val end = parts[1].trim().toIntOrNull() ?: return listOf(trimmed)
                val prefix = baseParts.dropLast(1).joinToString(".")
                return (start..end).map { "$prefix.$it" }
            }
        }
        return listOf(trimmed)
    }

    private fun expandCidr(baseIp: String, prefix: Int): List<String> {
        try {
            val addr = InetAddress.getByName(baseIp)
            val bytes = addr.address
            val ipInt = bytesToInt(bytes)
            val bits = 32 - prefix
            if (bits <= 0 || bits > 24) return listOf(baseIp)
            val mask = if (bits >= 32) 0 else (-1 shl bits)
            val masked = ipInt and mask
            val count = 1 shl bits
            return if (count > 65536) emptyList()
            else (1 until count - 1).map { intToIp(masked + it) }
        } catch (_: Exception) { return listOf(baseIp) }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun intToIp(value: Int): String {
        return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }

    fun getAvailableInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && ip.contains("."))
                        result.add(NetworkInterfaceInfo(name = ni.name, ip = ip, isActive = addr.isSiteLocalAddress))
                }
            }
        } catch (_: Exception) { }
        return result
    }

    fun getLocalIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (selectedInterfaceName.isNotEmpty() && ni.name != selectedInterfaceName) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && ip.contains(".")) return ip
                }
            }
            null
        } catch (_: Exception) { null }
    }

    fun getLocalNetworkPrefix(): String? {
        val ip = getLocalIp() ?: return null
        val parts = ip.split(".")
        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
        return null
    }

    /**
     * Auto-expand target to list of IPs.
     */
    fun autoExpandTarget(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        var cleaned = trimmed.replaceFirst("^https?://".toRegex(), "")

        // Check CIDR
        val isCidr = cleaned.contains("/") && cleaned.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/\d+"""))
        if (isCidr) return resolveTarget(cleaned)

        // ─── Partial prefix: "192." or "192.168." → scan ALL matching subnets ───
        if (cleaned.endsWith(".")) {
            val hasOneOctet = cleaned.matches(Regex("""\d{1,3}\."""))
            val hasTwoOctets = cleaned.matches(Regex("""\d{1,3}\.\d{1,3}\."""))
            if (hasOneOctet || hasTwoOctets) {
                val parts = cleaned.trimEnd('.').split(".")
                if (parts.size == 1 && parts[0] == "192") {
                    // "192." → scan all 192.168.x.x subnets
                    val result = mutableListOf<String>()
                    for (third in 0..255) {
                        for (last in 1..254) result.add("192.168.$third.$last")
                    }
                    return result
                } else if (parts.size == 2 && parts[0] == "192" && parts[1] == "168") {
                    // "192.168." → scan all 192.168.x.x subnets
                    val result = mutableListOf<String>()
                    for (third in 0..255) {
                        for (last in 1..254) result.add("192.168.$third.$last")
                    }
                    return result
                } else if (parts.size == 1 && parts[0] == "10") {
                    // "10." → use local prefix
                    val localPrefix = getLocalNetworkPrefix()
                    if (localPrefix != null) return (1..254).map { "$localPrefix.$it" }
                } else {
                    // Other prefixes → use local subnet
                    val localPrefix = getLocalNetworkPrefix()
                    if (localPrefix != null) return (1..254).map { "$localPrefix.$it" }
                }
            }
        }

        if (cleaned.contains("/")) cleaned = cleaned.substringBefore("/")
        cleaned = cleaned.substringBefore(":")
        cleaned = cleaned.trimEnd('.')

        if (cleaned.contains("-")) return resolveTarget(cleaned)

        val ipRegex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipRegex.matches(cleaned)) {
            val prefix = cleaned.substringBeforeLast(".")
            return (1..254).map { "$prefix.$it" }
        }

        // Domain name
        try {
            val addr = InetAddress.getByName(cleaned)
            val ip = addr.hostAddress ?: cleaned
            if (ipRegex.matches(ip)) {
                val prefix = ip.substringBeforeLast(".")
                return (1..254).map { "$prefix.$it" }
            }
            return listOf(ip)
        } catch (_: Exception) {
            return listOf(cleaned)
        }
    }

    fun arpScan(subnet: String): Set<String> {
        val live = Collections.synchronizedSet(mutableSetOf<String>())
        val pool = Executors.newFixedThreadPool(100)
        for (i in 1..254) {
            val ip = "$subnet$i"
            pool.execute {
                try { if (InetAddress.getByName(ip).isReachable(100)) live.add(ip) } catch (_: Exception) { }
            }
        }
        pool.shutdown()
        try { pool.awaitTermination(4, TimeUnit.SECONDS) } catch (_: Exception) { }
        return live
    }

    fun filterLiveHosts(targets: List<String>): List<String> {
        if (targets.isEmpty() || targets.size <= 1) return targets
        val localIp = getLocalIp()
        val isLocal = localIp != null && targets.any { it.startsWith(localIp.substringBeforeLast(".")) }
        if (isLocal) {
            val subnet = targets.first().substringBeforeLast(".") + "."
            val live = arpScan(subnet)
            if (live.isNotEmpty()) {
                val result = targets.filter { it in live }
                if (result.isNotEmpty()) return result
            }
        }
        return targets.filter { ip ->
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(ip, 80), 200)
                sock.close(); true
            } catch (_: Exception) {
                try { InetAddress.getByName(ip).isReachable(300) } catch (_: Exception) { false }
            }
        }
    }

    /**

    /**
     * Discover live hosts from a list of target IPs.
     * Handles single /24 subnet (fast arpScan) and multi-subnet (wide scan).
     */
    fun discoverLiveHosts(ips: List<String>): Set<String> {
        if (ips.size <= 1) return ips.toSet()
        val subnets = ips.map { it.substringBeforeLast(".") }.distinct()
        if (subnets.size <= 1) {
            // Single subnet: fast ARP scan
            return arpScan(subnets.first() + ".")
        } else if (subnets.size <= 256) {
            // Multi-subnet: wide parallel scan
            val parts = subnets.first().split(".")
            val basePrefix = parts.take(2).joinToString(".")
            val thirdRange = subnets.mapNotNull { it.split(".").getOrNull(2)?.toIntOrNull() }
            if (basePrefix.isNotEmpty() && thirdRange.isNotEmpty()) {
                val min3 = thirdRange.minOrNull() ?: 0
                val max3 = thirdRange.maxOrNull() ?: 255
                return filterLiveHostsWide(basePrefix, min3..max3).toSet()
            }
        }
        // Fallback: TCP quick-check on port 80 for all targets
        return ips.filter { ip ->
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(ip, 80), 100)
                sock.close(); true
            } catch (_: Exception) { false }
        }.toSet()
    }


     * Fast wide scan for large IP ranges like 192.168.x.x (65024 IPs).
     */
    fun filterLiveHostsWide(prefix: String, thirdRange: IntRange = 0..255): List<String> {
        val live = Collections.synchronizedSet(mutableSetOf<String>())
        val pool = Executors.newFixedThreadPool(100)
        for (third in thirdRange) {
            for (last in 1..254) {
                val ip = "$prefix.$third.$last"
                pool.execute {
                    try {
                        if (InetAddress.getByName(ip).isReachable(100)) live.add(ip)
                    } catch (_: Exception) { }
                }
            }
        }
        pool.shutdown()
        try { pool.awaitTermination(60, TimeUnit.SECONDS) } catch (_: Exception) { }
        return live.toList().sorted()
    }

    fun getLocalGateway(): String? {
        val prefix = getLocalNetworkPrefix() ?: return null
        return "$prefix.1"
    }

    fun getSubnetPrefix(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
        return null
    }

    fun resolveDomain(host: String): String? {
        return try { InetAddress.getByName(host).hostAddress } catch (_: Exception) { null }
    }

    fun getRelatedGateways(target: String): List<String> {
        val gateways = mutableListOf<String>()
        val prefix = getSubnetPrefix(target)
        if (prefix != null) { gateways.add("$prefix.1"); gateways.add("$prefix.254") }
        return gateways.distinct()
    }

    fun getLocalSubnet(): List<String>? {
        val prefix = getLocalNetworkPrefix() ?: return null
        return (1..254).map { "$prefix.$it" }
    }

    fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                        val ip = parts[0]; val mac = parts[3]
                        if (mac.count { it == ':' } == 5) result[ip] = mac.uppercase(Locale.ROOT)
                    }
                }
            }
        } catch (_: Exception) { }
        return result
    }

    fun lookupMacVendor(mac: String?): String? {
        if (mac == null) return null
        val key = mac.uppercase(Locale.ROOT).take(8)
        return vendorMap.entries.firstOrNull { (oui) ->
            key.startsWith(oui.uppercase(Locale.ROOT).take(8))
        }?.value
    }

    private val vendorMap = mapOf(
        "00:1A:2B" to "Cisco", "00:1B:21" to "D-Link", "00:0C:41" to "Netgear",
        "00:14:BF" to "TP-Link", "00:1A:3F" to "Huawei", "00:1D:0E" to "Xiaomi",
        "00:23:CD" to "ASUS", "00:17:C8" to "Belkin", "00:04:ED" to "Zyxel",
        "00:1A:E9" to "Buffalo", "00:24:01" to "Samsung", "00:1E:58" to "Apple",
        "00:1A:92" to "Sony", "00:1F:5B" to "Intel", "00:1B:11" to "Microsoft",
        "00:15:E9" to "Linksys", "00:22:6B" to "Ubiquiti", "00:1C:10" to "MikroTik",
        "00:0E:8F" to "Hikvision", "00:1C:CF" to "Dahua", "00:1B:44" to "Axis",
        "00:18:02" to "Foscam", "00:1E:06" to "Aruba", "00:23:DF" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
        "00:0C:29" to "VMware", "00:50:56" to "VMware", "00:15:5D" to "Hyper-V",
        "3C:07:54" to "Intel", "C8:3A:35" to "Intel", "A8:93:4A" to "Samsung",
        "F0:9F:C2" to "Samsung", "B0:75:D5" to "OnePlus", "AC:84:C6" to "Xiaomi",
        "58:CB:52" to "Huawei", "68:8F:84" to "Apple", "F8:2F:A8" to "Apple",
        "84:38:35" to "HP", "00:26:AB" to "Dell", "A4:4E:31" to "Lenovo",
        "28:D2:44" to "Amazon", "74:C2:2B" to "Amazon", "10:AE:60" to "Google",
        "18:1E:78" to "Google", "E0:AC:CB" to "ONVIF", "00:12:47" to "EDIMAX",
        "00:1A:4A" to "Tenda", "A0:21:B7" to "Tenda", "00:0F:E2" to "TOTOLINK"
    )
}
