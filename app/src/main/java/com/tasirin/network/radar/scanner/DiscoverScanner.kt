package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiscoverScanner {

    private val sharePorts = intArrayOf(21, 445, 139, 2049, 111, 135)
    private val allPorts = (intArrayOf(80, 554, 34567, 37777, 37215, 8080, 8899, 8554) +
        intArrayOf(8291, 7547, 5000, 23, 22, 161, 1900) + sharePorts).distinct().toList()

    fun scan(target: String, speed: ScanSpeed = ScanSpeed.SEDANG): Flow<ScanEvent> = channelFlow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            send(ScanEvent.Error("No IPs to scan"))
            return@channelFlow
        }

        val arpTable = NetworkUtils.readArpTable()

        // Scan SEMUA IP tanpa gate ping: banyak perangkat tidak membalas ICMP
        // tapi portnya terbuka — port scan jadi penentu host hidup (konsisten dgn PortScanner).
        ScanLoop.scanSubnets(subnets, speed, "Discover", scanOne = { ip ->
            val services = scanDiscoverPorts(ip, speed.timeoutMs)
            if (services.isEmpty()) null else ScanLoop.hostInfo(ip, arpTable, openPorts = services)
        }) { ev -> send(ev) }

        send(ScanEvent.Complete(ScanResult(type = ScanType.DISCOVER, target = target)))
    }

    private suspend fun scanDiscoverPorts(ip: String, timeoutMs: Int): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            allPorts.map { port ->
                async {
                    try {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                        sock.close()
                        val service = detectService(port)
                        if (service != null) PortInfo(port, service) else null
                    } catch (_: Exception) { null }
                }
            }.mapNotNull { it.await() }
        }
    }

    private fun detectService(port: Int): String? = when (port) {
        80, 8080 -> "Camera Web"
        554 -> "RTSP Stream"
        34567 -> "Hikvision SDK"
        37777 -> "Dahua SDK"
        37215 -> "Hikvision Backdoor"
        8899 -> "Camera Stream"
        8554 -> "RTSP Alt"
        8291 -> "Winbox (MikroTik)"
        7547 -> "TR-069"
        5000 -> "UPnP"
        23 -> "Telnet"
        22 -> "SSH"
        161 -> "SNMP"
        1900 -> "UPnP SSDP"
        21 -> "FTP"
        445 -> "SMB (File Sharing)"
        139 -> "NetBIOS"
        2049 -> "NFS"
        111 -> "Portmapper"
        135 -> "MSRPC"
        else -> null
    }
}
