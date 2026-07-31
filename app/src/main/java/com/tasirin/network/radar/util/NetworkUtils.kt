package com.tasirin.network.radar.util

import com.tasirin.network.radar.model.NetworkInterfaceInfo
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NetworkUtils {

    @Volatile var selectedInterfaceName: String = ""
    const val MAX_WIDE_IPS = 1024
    const val MAX_SUBNETS = 65536 // max /24 subnets materialized (≈16.7M hosts max)

    /**
     * Expand target to a list of /24 subnet prefixes ("192.168.1").
     * Supports: full IP, domain, CIDR, range (192.168.0.1-100),
     * partial prefix ("192.", "192.168.", "192.168.5.").
     * Partial prefixes expand to ALL matching /24 subnets, continuing
     * from the given octet up to 255 (e.g. "192.168." → 192.168.0 … 192.255.255).
     */
    fun expandTargetSubnets(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        var cleaned = trimmed.replaceFirst("^https?://".toRegex(), "")

        // CIDR → break into /24 subnets
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            val base = parts[0].trim()
            val prefix = parts[1].trim().toIntOrNull() ?: 24
            return expandCidrSubnets(base, prefix)
        }

        // Range: 192.168.0.1-254 → one /24 subnet
        if (cleaned.contains("-")) {
            val parts = cleaned.split("-")
            val startParts = parts[0].trim().split(".")
            if (startParts.size == 4) {
                val start = startParts[3].toIntOrNull()
                val end = parts[1].trim().toIntOrNull()
                if (start != null && end != null && start in 1..254 && end in 1..254 && start <= end) {
                    return listOf(startParts.dropLast(1).joinToString("."))
                }
            }
        }

        // Partial prefix with trailing dot
        if (cleaned.endsWith(".")) {
            val parts = cleaned.trimEnd('.').split(".")
            return when (parts.size) {
                1 -> {
                    // "192." → all 192.x.x.x /24 subnets
                    val a = parts[0].toIntOrNull() ?: return emptyList()
                    expandSubnetRange(a, 0, 255)
                }
                2 -> {
                    // "192.168." → 192.168.x → 192.255.x (continue sampai 255)
                    val a = parts[0].toIntOrNull() ?: return emptyList()
                    val bStart = parts[1].toIntOrNull() ?: return emptyList()
                    expandSubnetRange(a, bStart, 255)
                }
                3 -> {
                    // "192.168.5." → satu subnet
                    val prefix3 = cleaned.trimEnd('.')
                    if (prefix3.split(".").all { it.toIntOrNull() in 0..255 }) listOf(prefix3)
                    else emptyList()
                }
                else -> emptyList()
            }
        }

        // Full IP / domain → one /24 subnet
        cleaned = cleaned.substringBefore(":").trimEnd('.')
        val ip = resolveToIp(cleaned) ?: return emptyList()
        return listOf(ip.substringBeforeLast("."))
    }

    /** Generate /24 subnets for 1 octet + second-octet range. */
    private fun expandSubnetRange(a: Int, bStart: Int, bEnd: Int): List<String> {
        if (a !in 0..255 || bStart !in 0..255 || bEnd !in 0..255 || bStart > bEnd) return emptyList()
        // "10." sangat luas → batasi ke 16 /16 pertama biar wajar
        val maxB = if (a == 10 && bEnd - bStart > 15) (bStart + 15).coerceAtMost(255) else bEnd
        val result = mutableListOf<String>()
        val count = (maxB - bStart + 1) * 256
        if (count > MAX_SUBNETS) return emptyList()
        for (b in bStart..maxB) {
            for (c in 0..255) result.add("$a.$b.$c")
        }
        return result
    }

    private fun expandCidrSubnets(baseIp: String, prefix: Int): List<String> {
        try {
            val addr = InetAddress.getByName(baseIp)
            val ipInt = bytesToInt(addr.address)
            val hostBits = 32 - prefix
            if (hostBits < 8) return listOf(intToIp(ipInt).substringBeforeLast("."))
            val totalSubnets = 1 shl (hostBits - 8)
            if (totalSubnets > MAX_SUBNETS) return emptyList()
            val mask = if (hostBits >= 32) 0 else (-1 shl hostBits)
            val start = ipInt and mask
            val result = mutableListOf<String>()
            for (i in 0 until totalSubnets) {
                result.add(intToIp(start + (i shl 8)).substringBeforeLast("."))
            }
            return result
        } catch (_: Exception) { return emptyList() }
    }

    private fun resolveToIp(host: String): String? {
        if (host.isBlank()) return null
        return try {
            val addr = InetAddress.getByName(host)
            addr.hostAddress?.takeIf { it.count { c -> c == '.' } == 3 }
        } catch (_: Exception) { null }
    }

    /** Expand a /24 subnet prefix to its 254 host IPs. */
    fun expandSubnetHosts(subnet: String): List<String> {
        return (1..254).map { "$subnet.$it" }
    }

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

    fun isWideScan(input: String): Boolean {
        return expandTargetSubnets(input).size > 1
    }

    /**
     * Auto-expand target to a materialized IP list.
     * Only returns when the target is small (≤512 subnets); huge targets
     * should use expandTargetSubnets() instead to avoid OOM.
     */
    fun autoExpandTarget(input: String): List<String> {
        val subnets = expandTargetSubnets(input)
        if (subnets.isEmpty() || subnets.size > 512) return emptyList()
        val result = ArrayList<String>(subnets.size * 254)
        for (subnet in subnets) {
            for (i in 1..254) result.add("$subnet.$i")
        }
        return result
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
        val subnets = targets.map { it.substringBeforeLast(".") }.distinct()
        val isWide = subnets.size > 4 || targets.size > MAX_WIDE_IPS
        if (isWide) {
            return tcpQuickScan(targets, 300)
        }
        val localIp = getLocalIp()
        val localPrefix = localIp?.let { it.substringBeforeLast(".") }
        val isLocal = localPrefix != null && targets.any { it.startsWith(localPrefix) }
        if (isLocal) {
            val subnet = localPrefix + "."
            val arpLive = arpScan(subnet)
            if (arpLive.isNotEmpty()) {
                val result = targets.filter { it in arpLive }
                if (result.isNotEmpty()) return result
            }
        }
        return tcpQuickScan(targets, 200)
    }

    fun tcpQuickScan(ips: List<String>, timeoutMs: Int = 200): List<String> {
        val live = Collections.synchronizedList(mutableListOf<String>())
        val pool = Executors.newFixedThreadPool(200)
        val latch = CountDownLatch(ips.size)
        for (ip in ips) {
            pool.execute {
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(ip, 80), timeoutMs)
                    sock.close()
                    live.add(ip)
                } catch (_: Exception) { }
                latch.countDown()
            }
        }
        pool.shutdown()
        try { latch.await(30, TimeUnit.SECONDS) } catch (_: Exception) { }
        return live.toList().sorted()
    }

    /**
     * Discover live hosts from a list of target IPs.
     */
    fun discoverLiveHosts(ips: List<String>): Set<String> {
        if (ips.size <= 1) return ips.toSet()
        val subnets = ips.map { it.substringBeforeLast(".") }.distinct()
        if (subnets.size <= 1) {
            return arpScan(subnets.first() + ".")
        } else if (subnets.size <= 256 && ips.size <= MAX_WIDE_IPS) {
            return tcpQuickScan(ips, 300).toSet()
        }
        return ips.toSet()
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
