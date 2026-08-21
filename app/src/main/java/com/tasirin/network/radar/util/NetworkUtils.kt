package com.tasirin.network.radar.util

import com.tasirin.network.radar.model.NetworkInterfaceInfo
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

object NetworkUtils {

    @Volatile var selectedInterfaceName: String = ""
    const val MAX_SUBNETS = 65536 // max /24 subnets materialized (≈16.7M hosts max)

    /**
     * Target scan per subnet: prefix /24 (3 oktet) + rentang host di dalamnya.
     * Default host 1..254 (melewati alamat network .0 dan broadcast .255).
     */
    data class SubnetTarget(
        val prefix: String,
        val hostStart: Int = 1,
        val hostEnd: Int = 254
    )

    /**
     * Expand target ke daftar [SubnetTarget].
     * Mendukung: IP penuh, domain, CIDR, rentang (192.168.0.1-254,
     * 192.168.15.1-192.168.16.1 lintas subnet), dan awalan parsial
     * ("192", "192.168.", "192.168.5", "192.168.15.1").
     * Semua input numerik LANJUT dari titik yang diketik sampai 255, jadi
     * "192.168.15.1" otomatis meneruskan ke 192.168.16.x … 192.168.255.x
     * (tidak berhenti di 192.168.15.255).
     */
    fun expandTargetSubnets(input: String): List<SubnetTarget> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        var cleaned = trimmed.replaceFirst("^https?://".toRegex(), "")

        // CIDR → pecah menjadi /24
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            val base = parts[0].trim()
            val prefix = parts[1].trim().toIntOrNull() ?: 24
            return expandCidrSubnets(base, prefix)
        }

        // Rentang bertanda "-": "192.168.0.1-254" atau "192.168.15.1-192.168.16.1"
        if (cleaned.contains("-")) {
            val rangeParts = cleaned.split("-")
            if (rangeParts.size == 2) {
                val startParts = rangeParts[0].trim().split(".").mapNotNull { it.toIntOrNull() }
                val endParts = rangeParts[1].trim().split(".").mapNotNull { it.toIntOrNull() }
                if (startParts.size == 4 && startParts.all { it in 0..255 }) {
                    // "192.168.0.1-254" → satu subnet dengan rentang host
                    if (endParts.size == 1 && endParts[0] in 0..255 && startParts[3] <= endParts[0]) {
                        return listOf(SubnetTarget(
                            startParts.dropLast(1).joinToString("."),
                            hostStart = startParts[3], hostEnd = endParts[0]))
                    }
                    // "192.168.15.1-192.168.16.1" → rentang penuh lintas subnet
                    if (endParts.size == 4 && endParts.all { it in 0..255 }) {
                        return expandIpRange(startParts, endParts)
                    }
                }
            }
        }

        // Awalan numerik (dengan/tanpa titik akhir): "192", "192.16", "192.168.",
        // "192.168.5", "192.168.15.1" → lanjut dari titik yang diketik sampai 255
        cleaned = cleaned.substringBefore(":").trimEnd('.')
        val parts = cleaned.split(".")
        if (parts.size in 1..4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val octets = parts.map { it.toInt() }
            if (octets.any { it > 255 }) return emptyList()
            return when (parts.size) {
                1 -> expandSubnets(octets[0], 0, 255)              // "192" → 192.0.x … 192.255.x
                2 -> expandSubnets(octets[0], octets[1], 255)      // "192.16" → 192.16.x … 192.255.x
                3 -> expandSubnets(octets[0], octets[1], octets[1], octets[2], 255)
                    // "192.168.5" → 192.168.5 … 192.168.255
                4 -> expandSubnets(octets[0], octets[1], octets[1], octets[2], 255)
                    .mapIndexed { i, t -> if (i == 0) t.copy(hostStart = octets[3]) else t }
                    // "192.168.15.1" → 192.168.15.1 … 192.168.255.254
                else -> emptyList()
            }
        }

        // Domain → satu subnet /24
        val ip = resolveDomain(cleaned) ?: return emptyList()
        return listOf(SubnetTarget(ip.substringBeforeLast(".")))
    }

    /** Bangun daftar subnet /24 dari rentang oktet-2 dan oktet-3. */
    private fun expandSubnets(a: Int, bFrom: Int, bTo: Int, cFrom: Int = 0, cTo: Int = 255): List<SubnetTarget> {
        if (a !in 0..255 || bFrom !in 0..255 || bTo !in 0..255 || bFrom > bTo ||
            cFrom !in 0..255 || cTo !in 0..255 || cFrom > cTo) return emptyList()
        val count = (bTo - bFrom + 1) * (cTo - cFrom + 1)
        if (count > MAX_SUBNETS) return emptyList()
        val result = mutableListOf<SubnetTarget>()
        for (b in bFrom..bTo) {
            for (c in cFrom..cTo) result.add(SubnetTarget("$a.$b.$c"))
        }
        return result
    }

    /** Rentang dua IP penuh → subnet antara (boleh lintas subnet), host dibatasi ujung-ujungnya. */
    private fun expandIpRange(start: List<Int>, end: List<Int>): List<SubnetTarget> {
        val startInt = ipToLong(start)
        val endInt = ipToLong(end)
        if (startInt > endInt) return emptyList()
        val startSub = startInt and 0xFFFFFF00L
        val endSub = endInt and 0xFFFFFF00L
        val subnetCount = ((endSub - startSub) / 256 + 1).toInt()
        if (subnetCount > MAX_SUBNETS) return emptyList()
        val result = mutableListOf<SubnetTarget>()
        for (i in 0 until subnetCount) {
            val prefix = intToIp((startSub + i * 256L).toInt()).substringBeforeLast(".")
            result.add(SubnetTarget(
                prefix,
                hostStart = if (i == 0) start[3] else 1,
                hostEnd = if (i == subnetCount - 1) end[3] else 254
            ))
        }
        return result
    }

    private fun ipToLong(parts: List<Int>): Long =
        (parts[0].toLong() shl 24) or (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or parts[3].toLong()

    private fun expandCidrSubnets(baseIp: String, prefix: Int): List<SubnetTarget> {
        try {
            val addr = InetAddress.getByName(baseIp)
            val ipInt = bytesToInt(addr.address)
            val hostBits = 32 - prefix
            if (hostBits < 8) return listOf(SubnetTarget(intToIp(ipInt).substringBeforeLast(".")))
            val totalSubnets = 1 shl (hostBits - 8)
            if (totalSubnets > MAX_SUBNETS) return emptyList()
            val mask = if (hostBits >= 32) 0 else (-1 shl hostBits)
            val start = ipInt and mask
            val result = mutableListOf<SubnetTarget>()
            for (i in 0 until totalSubnets) {
                result.add(SubnetTarget(intToIp(start + (i shl 8)).substringBeforeLast(".")))
            }
            return result
        } catch (_: Exception) { return emptyList() }
    }

    /** Resolve a hostname/domain to its IPv4 address, or null if it fails. */
    fun resolveDomain(host: String): String? {
        if (host.isBlank()) return null
        return try {
            val addr = InetAddress.getByName(host)
            addr.hostAddress?.takeIf { it.count { c -> c == '.' } == 3 }
        } catch (_: Exception) { null }
    }

    /** Expand satu subnet ke daftar IP host sesuai rentang [SubnetTarget]. */
    fun expandSubnetHosts(subnet: SubnetTarget): List<String> {
        val start = subnet.hostStart.coerceIn(0, 255)
        val end = subnet.hostEnd.coerceIn(0, 255)
        if (start > end) return emptyList()
        return (start..end).map { "${subnet.prefix}.$it" }
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

    fun getLocalGateway(): String? {
        // 1) Baca gateway asli dari tabel routing ("default via x.x.x.x")
        var process: Process? = null
        try {
            process = ProcessBuilder("ip", "route").redirectErrorStream(true).start()
            val out = process!!.inputStream.bufferedReader().readText()
            process.waitFor()
            val via = out.lineSequence()
                .firstOrNull { it.trimStart().startsWith("default") }
                ?.split("\\s+".toRegex())
                ?.getOrNull(2)
            if (via != null && via.count { it == '.' } == 3) return via
        } catch (_: Exception) {
        } finally {
            process?.destroy()
        }
        // 2) Fallback /proc/net/route (gateway dalam hex little-endian)
        try {
            val lines = java.io.File("/proc/net/route").readLines()
            for (line in lines.drop(1)) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 3 && parts[1] == "00000000") {
                    hexToIp(parts[2])?.let { return it }
                }
            }
        } catch (_: Exception) { }
        // 3) Asumsi lama: gateway di .1
        val prefix = getLocalNetworkPrefix() ?: return null
        return "$prefix.1"
    }

    /** Konversi gateway format hex little-endian (/proc/net/route) ke IPv4. */
    private fun hexToIp(hex: String): String? {
        if (hex.length != 8) return null
        return try {
            val a = hex.substring(6, 8).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            val c = hex.substring(2, 4).toInt(16)
            val d = hex.substring(0, 2).toInt(16)
            if (a == 0 && b == 0 && c == 0 && d == 0) null else "$a.$b.$c.$d"
        } catch (_: Exception) { null }
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
        return vendorList.firstOrNull { (oui) -> key.startsWith(oui) }?.second
    }

    // Precomputed uppercase OUI prefixes agar lookup cepat tanpa re-uppercase tiap call
    private val vendorList: List<Pair<String, String>> by lazy {
        vendorMap.map { (oui, vendor) -> oui.uppercase(Locale.ROOT).take(8) to vendor }
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
