package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiscoverScanner {

    private val sharePorts = intArrayOf(21, 445, 139, 2049, 111, 135)

    fun scan(target: String): Flow<ScanEvent> = channelFlow {
        val ips = NetworkUtils.autoExpandTarget(target)
        if (ips.isEmpty()) {
            send(ScanEvent.Error("No IPs to scan"))
            return@channelFlow
        }

        // ARP pre-scan like v1.0
        var scanIps = ips.toList()
        val localIp = NetworkUtils.getLocalIp()
        val isLocal = localIp != null && ips.any { it.startsWith(localIp.substringBeforeLast(".")) }
        if (isLocal && ips.size > 1) {
            send(ScanEvent.Progress("Discovering live hosts...", 0, ips.size))
            val subnet = ips.first().substringBeforeLast(".") + "."
            val live = NetworkUtils.arpScan(subnet)
            if (live.isNotEmpty()) scanIps = ips.filter { it in live }
        }

        val total = scanIps.size
        val arpTable = NetworkUtils.readArpTable()

        for ((idx, ip) in scanIps.withIndex()) {
            send(ScanEvent.Progress(ip, idx, total))

            // Quick ping check
            val alive = PingUtil.ping(ip, 500) != null
            if (!alive) continue

            val mac = arpTable[ip]
            val vendor = NetworkUtils.lookupMacVendor(mac)
            val hostname = try {
                val hn = java.net.InetAddress.getByName(ip).hostName
                if (hn != ip) hn else null
            } catch (_: Exception) { null }

            // Parallel scan all discover ports on this host
            val services = scanDiscoverPorts(ip)

            if (services.isNotEmpty()) {
                send(ScanEvent.HostFound(HostInfo(
                    ip = ip, hostname = hostname, macAddress = mac, macVendor = vendor,
                    isAlive = true, openPorts = services
                )))
            }
        }

        send(ScanEvent.Complete(ScanResult(type = ScanType.DISCOVER, target = target)))
    }

    private suspend fun scanDiscoverPorts(ip: String): List<PortInfo> = withContext(Dispatchers.IO) {
        val cameraPorts = intArrayOf(80, 554, 34567, 37777, 37215, 8080, 8899, 8554)
        val routerPorts = intArrayOf(8291, 7547, 5000, 23, 22, 161, 1900)
        val allPorts = (cameraPorts + routerPorts + sharePorts).distinct().toList()

        coroutineScope {
            allPorts.map { port ->
                async {
                    try {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), 200)
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
