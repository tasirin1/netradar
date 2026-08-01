package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Scan port UDP umum: DNS, NTP, SNMP, NetBIOS, SSDP, mDNS.
 * UDP tidak punya jabat tangan, jadi deteksi = kirim probe lalu tunggu balasan.
 * (Port yang terbuka biasanya membalas; yang tertutup/filter tidak membalas apa pun.)
 */
class UdpScanner {

    fun scan(target: String, speed: ScanSpeed = ScanSpeed.SEDANG): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val hostConcurrency = if (isWide) speed.hostWide else speed.hostLocal
        val udpTimeout = (speed.timeoutMs * 5).coerceIn(800, 1500)
        val arpTable = NetworkUtils.readArpTable()
        var completed = 0L
        var found = 0
        val startMs = System.currentTimeMillis()

        emit(ScanEvent.Progress(
            "UDP scan ${subnets.size} subnet — ${subnets.first()} … ${subnets.last()} (${total} IP)",
            0, total.toInt()))

        val totalSubnets = subnets.size
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"

            emit(ScanEvent.Progress("$subnetLabel — $subnet.0/24 (UDP)", completed.toInt(), total.toInt()))

            ScanLoop.scanIps(ips, hostConcurrency, scanOne = { ip ->
                val open = coroutineScope {
                    UDP_PORTS.map { (port, service) ->
                        async {
                            if (UdpProbe.probe(ip, port, udpTimeout)) PortInfo(port = port, service = "$service (UDP)")
                            else null
                        }
                    }.mapNotNull { it.await() }
                }
                if (open.isEmpty()) null
                else ScanLoop.hostInfo(ip, arpTable, openPorts = open.sortedBy { it.port })
            }) { ip, host ->
                completed++
                if (host != null) { found++; emit(ScanEvent.HostFound(host)) }
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                emit(ScanEvent.Progress("$subnetLabel · $ip · $found ditemukan · ${elapsed}s", completed.toInt(), total.toInt()))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.UDP, target = target)))
    }

    companion object {
        private val UDP_PORTS = listOf(
            53 to "DNS",
            123 to "NTP",
            137 to "NetBIOS",
            161 to "SNMP",
            1900 to "SSDP",
            5353 to "mDNS"
        )
    }
}

/** Probe UDP: kirim paket sesuai service lalu tunggu balasan dalam [timeoutMs]. */
object UdpProbe {

    fun probe(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val data = probePacket(port)
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), port))
            val buf = ByteArray(2048)
            socket.receive(DatagramPacket(buf, buf.size))
            true
        }
    } catch (_: SocketTimeoutException) { false }
    catch (_: Exception) { false }

    private fun probePacket(port: Int): ByteArray = when (port) {
        53 -> dnsQuery("netradar.local", type = 1)
        123 -> ByteArray(48).also { it[0] = 0x1B.toByte() } // NTP client request
        137 -> netbiosStatusQuery()
        161 -> snmpGetRequest()
        1900 -> "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n".toByteArray()
        5353 -> dnsQuery("netradar.local", type = 1)
        else -> ByteArray(0)
    }

    /** Query DNS sederhana (header 12 byte + nama + type/class). */
    private fun dnsQuery(name: String, type: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x12); out.write(0x34)             // ID
        out.write(0x01); out.write(0x00)             // flags: RD
        out.write(0x00); out.write(0x01)             // QDCOUNT
        out.write(0x00); out.write(0x00)             // ANCOUNT
        out.write(0x00); out.write(0x00)             // NSCOUNT
        out.write(0x00); out.write(0x00)             // ARCOUNT
        name.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00)                              // root
        out.write(type shr 8); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01)             // class IN
        return out.toByteArray()
    }

    /** Query status NetBIOS (NBSTAT) untuk nama "NETBIOS". */
    private fun netbiosStatusQuery(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x12); out.write(0x34)             // transaction ID
        out.write(0x00); out.write(0x10)             // flags
        out.write(0x00); out.write(0x01)             // QDCOUNT
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        // nama 32 byte (first-level encoding): 16 char termasuk suffix 0x00
        val chars = ("NETBIOS".padEnd(15, ' ') + 0x00.toChar()).toCharArray()
        val encoded = ByteArray(32)
        chars.forEachIndexed { i, c ->
            val b = c.code and 0xFF
            encoded[i * 2] = ((b shr 4) + 0x41).toByte()
            encoded[i * 2 + 1] = ((b and 0x0F) + 0x41).toByte()
        }
        out.write(encoded)
        out.write(0x00); out.write(0x21)             // type NBSTAT
        out.write(0x00); out.write(0x01)             // class IN
        return out.toByteArray()
    }

    /** SNMPv1 GET sysDescr.0 dengan community "public". */
    private fun snmpGetRequest(): ByteArray {
        val oid = byteArrayOf(0x06, 0x08, 0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00) // 1.3.6.1.2.1.1.1.0
        val varbind = snmpSeq(0x30, oid + byteArrayOf(0x05, 0x00))
        val varbinds = snmpSeq(0x30, varbind)
        val pdu = snmpSeq(0xA0.toByte(), intBytes(1, 0x7FFFFFFF) + byteArrayOf(0x02, 0x01, 0x00) + byteArrayOf(0x02, 0x01, 0x00) + varbinds)
        val body = byteArrayOf(0x02, 0x01, 0x00) + byteArrayOf(0x04, 0x06) + "public".toByteArray() + pdu
        return snmpSeq(0x30, body)
    }

    private fun snmpSeq(tag: Byte, content: ByteArray): ByteArray =
        byteArrayOf(tag) + len(content.size) + content

    private fun len(n: Int): ByteArray = when {
        n < 128 -> byteArrayOf(n.toByte())
        n < 256 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), (n and 0xFF).toByte())
    }

    private fun intBytes(tag: Int, value: Int): ByteArray {
        var v = value
        val tmp = ArrayList<Byte>()
        do {
            tmp.add(0, (v and 0xFF).toByte())
            v = v ushr 8
        } while (v > 0)
        return byteArrayOf(tag.toByte(), tmp.size.toByte()) + tmp.toByteArray()
    }
}
