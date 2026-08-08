package com.tasirin.network.radar.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolver nama perangkat mDNS/SSDP (best-effort) untuk dipakai sebagai
 * fallback saat reverse DNS gagal. Kirim query mDNS PTR + SSDP M-SEARCH
 * sekali, lalu kumpulkan balasan unicast dalam jendela [timeoutMs].
 * Tidak butuh MulticastLock/join grup: balasan mDNS diminta lewat bit QU
 * (unicast response) dan balasan SSDP memang selalu unicast.
 */
object MdnsNameResolver {

    private val cache = ConcurrentHashMap<String, String>()
    @Volatile private var cachedAt = 0L

    /** Perbarui cache bila sudah basi; tidak pernah melempar error. */
    suspend fun refreshIfStale(maxAgeMs: Long = 30_000) {
        if (System.currentTimeMillis() - cachedAt < maxAgeMs) return
        try {
            val names = withContext(Dispatchers.IO) { discover(timeoutMs = 1200) }
            if (names.isNotEmpty()) {
                cache.putAll(names)
                cachedAt = System.currentTimeMillis()
            }
        } catch (_: Exception) { }
    }

    fun nameFor(ip: String): String? = cache[ip]?.takeIf { it.isNotBlank() }

    /** Kirim query dan kumpulkan nama perangkat (ip -> nama). */
    fun discover(timeoutMs: Long = 1200): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            DatagramSocket().use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = 250
                val mcast = InetAddress.getByName("224.0.0.251")
                val ssdp = InetAddress.getByName("239.255.255.250")
                SERVICE_TYPES.forEach { type ->
                    val q = ptrQuery(type)
                    socket.send(DatagramPacket(q, q.size, mcast, 5353))
                }
                val search = M_SEARCH.toByteArray()
                socket.send(DatagramPacket(search, search.size, ssdp, 1900))

                val deadline = System.nanoTime() + timeoutMs * 1_000_000
                val buf = ByteArray(4096)
                while (System.nanoTime() < deadline) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        socket.receive(pkt)
                        if (pkt.length < 8) continue
                        when (pkt.port) {
                            5353 -> parseDns(buf, pkt.length).forEach { (ip, name) ->
                                if (name.isNotEmpty() && ip.isNotEmpty()) out[ip] = name
                            }
                            1900 -> {
                                val src = pkt.address?.hostAddress ?: continue
                                parseSsdp(buf, pkt.length)?.let { out[src] = it }
                            }
                        }
                    } catch (_: SocketTimeoutException) { /* tunggu sampai deadline */ }
                }
            }
        } catch (_: Exception) { }
        return out
    }

    private fun ptrQuery(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0); out.write(0)        // ID (0 untuk mDNS)
        out.write(0); out.write(0)        // flags
        out.write(0); out.write(1)        // QDCOUNT
        out.write(0); out.write(0)        // ANCOUNT
        out.write(0); out.write(0)        // NSCOUNT
        out.write(0); out.write(0)        // ARCOUNT
        name.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)                      // root
        out.write(0); out.write(12)       // type PTR
        out.write(0x80); out.write(1)     // class IN + QU (minta balasan unicast)
        return out.toByteArray()
    }

    /** Parse paket DNS (jawaban mDNS) -> peta ip -> nama instance layanan. */
    internal fun parseDns(data: ByteArray, length: Int): Map<String, String> {
        val out = mutableMapOf<String, String>()
        if (length < 12) return out
        try {
            val p = DnsCursor(data, length)
            p.u16(); p.u16()                         // ID + flags
            val qd = p.u16(); val an = p.u16(); val ns = p.u16(); val ar = p.u16()
            repeat(qd) { p.name(); p.u16(); p.u16() }

            data class Srv(val instance: String?, val target: String)
            val srvs = mutableListOf<Srv>()
            val aRecords = mutableMapOf<String, String>()
            var lastPtr: String? = null

            fun parseSection(count: Int) {
                repeat(count) {
                    val name = p.name()
                    val type = p.u16(); p.u16()      // type + class
                    p.u16(); p.u16()                 // TTL
                    val rdlen = p.u16()
                    when (type) {
                        12 -> lastPtr = p.name()     // PTR -> nama instance
                        33 -> {                      // SRV
                            p.u16(); p.u16()         // priority + weight
                            p.u16()                  // port
                            srvs.add(Srv(lastPtr, p.name()))
                        }
                        1 -> {                       // A
                            val ip = if (rdlen >= 4) {
                                val b1 = p.u8(); val b2 = p.u8(); val b3 = p.u8(); val b4 = p.u8()
                                p.skip(rdlen - 4)
                                "$b1.$b2.$b3.$b4"
                            } else { p.skip(rdlen); "" }
                            if (ip.isNotEmpty()) aRecords[name] = ip
                        }
                        else -> p.skip(rdlen)
                    }
                }
            }
            parseSection(an)
            parseSection(ns)
            parseSection(ar)

            srvs.forEach { srv ->
                val ip = aRecords[srv.target] ?: return@forEach
                val instance = cleanInstance(srv.instance ?: return@forEach)
                if (instance.isNotEmpty()) out[ip] = instance
            }
        } catch (_: Exception) { }
        return out
    }

    /** Parse balasan SSDP -> nama perangkat dari header SERVER/USN. */
    internal fun parseSsdp(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val text = String(data, 0, length, Charsets.ISO_8859_1)
        if (!text.startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val server = headerValue(text, "SERVER:") ?: return null
        val usn = headerValue(text, "USN:")
        val serverName = server.substringBefore("/").trim()
        val vendor = if (serverName.endsWith(" UPnP", ignoreCase = true)) serverName.dropLast(5).trim() else serverName
            .takeIf { it.isNotBlank() && !it.equals("UPnP", true) && !it.equals("Linux", true) &&
                !it.startsWith("http", true) && !it.equals("Windows", true) }
        val type = usn?.let { Regex("device:([^:]+)").find(it)?.groupValues?.get(1) }
            ?.let { when {
                it.equals("MediaRenderer", true) -> "Media Player"
                it.equals("MediaServer", true) -> "Media Server"
                else -> it
            } }
        return listOfNotNull(vendor, type).joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }

    private fun headerValue(text: String, key: String): String? =
        text.lines().firstOrNull { it.startsWith(key, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    /** "TV-Salon._airplay._tcp.local" -> "TV-Salon"; buang escape spasi mDNS. */
    private fun cleanInstance(name: String): String {
        val n = name.substringBefore("._").replace("\\032", " ").trim()
        return n.takeIf { it.isNotEmpty() && it.length <= 40 } ?: ""
    }

    private class DnsCursor(private val data: ByteArray, private val limit: Int) {
        var pos = 0
        fun u8(): Int = if (pos < limit) data[pos++].toInt() and 0xFF else 0
        fun u16(): Int = (u8() shl 8) or u8()
        fun skip(n: Int) { pos += n }

        fun name(): String {
            val sb = StringBuilder()
            var jumps = 0
            var p = pos
            var broken = false
            while (true) {
                if (p >= limit) { broken = true; break }
                val b = data[p].toInt() and 0xFF
                when {
                    b == 0 -> { if (jumps == 0) pos = p + 1; break }
                    b and 0xC0 == 0xC0 -> {
                        if (p + 1 >= limit) { broken = true; break }
                        if (jumps == 0) pos = p + 2
                        p = ((b and 0x3F) shl 8) or (data[p + 1].toInt() and 0xFF)
                        if (++jumps > 24) { broken = true; break }
                    }
                    else -> {
                        if (p + 1 + b > limit) { broken = true; break }
                        if (sb.isNotEmpty()) sb.append('.')
                        for (i in 0 until b) sb.append((data[p + 1 + i].toInt() and 0xFF).toChar())
                        p += 1 + b
                    }
                }
            }
            return if (broken) "" else sb.toString()
        }
    }

    private const val M_SEARCH =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n"

    private val SERVICE_TYPES = listOf(
        "_http._tcp.local", "_https._tcp.local",
        "_airplay._tcp.local", "_googlecast._tcp.local", "_roku-ecp._tcp.local",
        "_kodi._tcp.local", "_spotify-connect._tcp.local",
        "_ipp._tcp.local", "_smb._tcp.local", "_ssh._tcp.local", "_workstation._tcp.local"
    )
}
