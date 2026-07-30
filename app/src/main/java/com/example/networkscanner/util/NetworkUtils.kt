package com.tasirin.network.radar.util

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

    fun resolveTarget(input: String): List<String> {
        val trimmed = input.trim()
        // Check if CIDR
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val baseIp = parts[0]
            val prefix = parts[1].toIntOrNull() ?: 24
            return expandCidr(baseIp, prefix)
        }
        // Check if range like 192.168.0.1-20
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
        } catch (_: Exception) {
            return listOf(baseIp)
        }
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

    fun getLocalIp(): String? { return getLocalIpForInterface(forcedInterface) }
    private fun getLocalIpImpl(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
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
     * Automatically expand a single IP to full /24 subnet (like v1.0).
     * For domain names, resolve to IP first then expand.
     * For CIDR/range, resolve as-is.
     */
    fun autoExpandTarget(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Remove protocol prefix like http:// or https://
        var cleaned = trimmed.replaceFirst("^https?://".toRegex(), "")

        // Check if it's a CIDR first (before stripping paths)
        val isCidr = cleaned.contains("/") && cleaned.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/\d+"""))
        if (isCidr) {
            return resolveTarget(cleaned)
        }

        // Strip trailing path (only if not a pure IP with port)
        if (cleaned.contains("/")) {
            cleaned = cleaned.substringBefore("/")
        }

        // Strip port number
        cleaned = cleaned.substringBefore(":")

        // Strip trailing dots or spaces
        cleaned = cleaned.trimEnd('.')

        // Check for IP range
        if (cleaned.contains("-")) {
            return resolveTarget(cleaned)
        }

        // Check if it's a single IP
        val ipRegex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipRegex.matches(cleaned)) {
            val prefix = cleaned.substringBeforeLast(".")
            // Like v1.0: expand single IP to /24 subnet (1..254)
            return (1..254).map { "$prefix.$it" }
        }

        // Domain name or hostname — resolve to IP (like v1.0)
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

    /**
     * Like v1.0: use InetAddress.isReachable with 50 threads
     * to quickly find live hosts on a subnet.
     */
    fun arpScan(subnet: String): Set<String> {
        val live = Collections.synchronizedSet(mutableSetOf<String>())
        val pool = Executors.newFixedThreadPool(50)
        for (i in 1..254) {
            val ip = "$subnet$i"
            pool.execute {
                try { if (InetAddress.getByName(ip).isReachable(300)) live.add(ip) } catch (_: Exception) { }
            }
        }
        pool.shutdown()
        try { pool.awaitTermination(5, TimeUnit.SECONDS) } catch (_: Exception) { }
        return live
    }

    /**
     * Fast pre-scan: returns only live IPs from the target list.
     * For local subnets, uses ARP-like isReachable discovery.
     * Falls back to TCP ping on port 80.
     */
    fun filterLiveHosts(targets: List<String>): List<String> {
        if (targets.isEmpty() || targets.size <= 1) return targets

        val localIp = getLocalIp()
        val isLocal = localIp != null && targets.any {
            it.startsWith(localIp.substringBeforeLast("."))
        }

        if (isLocal) {
            val subnet = targets.first().substringBeforeLast(".") + "."
            val live = arpScan(subnet)
            if (live.isNotEmpty()) {
                val result = targets.filter { it in live }
                if (result.isNotEmpty()) return result
            }
        }

        // Fallback: quick TCP connect on port 80 + ICMP
        return targets.filter { ip ->
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(ip, 80), 200)
                sock.close()
                true
            } catch (_: Exception) {
                try { InetAddress.getByName(ip).isReachable(300) } catch (_: Exception) { false }
            }
        }
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

    /**
     * Resolve domain name to IP.
     */
    fun resolveDomain(host: String): String? {
        return try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) { null }
    }

    /**
     * Get all gateway IPs related to this target.
     */
    fun getRelatedGateways(target: String): List<String> {
        val gateways = mutableListOf<String>()
        val prefix = getSubnetPrefix(target)
        if (prefix != null) {
            gateways.add("$prefix.1")
            gateways.add("$prefix.254")
        }
        return gateways.distinct()
    }

    fun getLocalSubnet(): List<String>? {
        val prefix = getLocalNetworkPrefix() ?: return null
        return (1..254).map { "$prefix.$it" }
    }

    /**
     * Read ARP table for MAC addresses, like v1.0.
     */
    fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac.count { it == ':' } == 5) {
                            result[ip] = mac.uppercase(Locale.ROOT)
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return result
    }

    /**
     * Lookup MAC vendor from embedded OUI database.
     */
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

    // ─── Network Interface Selection ───
    @Volatile
    private var forcedInterface: String? = null

    fun setActiveInterface(name: String?) { forcedInterface = name }

    fun getActiveInterfaceName(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && ip.contains(".")) return ni.name
                }
            }
            null
        } catch (_: Exception) { null }
    }

    fun getAvailableInterfaces(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val name = ni.name
                val addrs = ni.inetAddresses
                var hasIpv4 = false
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                        hasIpv4 = true
                        break
                    }
                }
                if (hasIpv4 && (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("rmnet") || name.startsWith("p2p") || name.startsWith("usb"))) {
                    result.add(name)
                }
            }
        } catch (_: Exception) { }
        return result.distinct()
    }

    // Override getLocalIp to respect forced interface
    private fun getLocalIpForInterface(ifaceName: String?): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ifaceName != null && ni.name != ifaceName) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val ip = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && ip.contains(".")) {
                        if (ifaceName != null) return ip
                        if (ni.name.startsWith("wlan") || ni.name.startsWith("eth") || ni.name.startsWith("rmnet")) return ip
                    }
                }
            }
            null
        } catch (_: Exception) { null }
    }

    // Override the original getLocalIp to use forced interface
    private val _originalGetLocalIp = { getLocalIp() }
