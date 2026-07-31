package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiscoverScanner {

    private val sharePorts = intArrayOf(21, 445, 139, 2049, 111, 135)

    fun scan(target: String): Flow<ScanEvent> = channelFlow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            send(ScanEvent.Error("No IPs to scan"))
            return@channelFlow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val hostConcurrency = if (isWide) 20 else 5
        val arpTable = NetworkUtils.readArpTable()
        var completed = 0L

        send(ScanEvent.Progress("Discover ${subnets.size} subnet(s), ${total} IP(s)...", 0, total.toInt()))

        subnets.forEach { subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val liveIps = if (isWide) ips else NetworkUtils.discoverLiveHosts(ips).toList()
            val scanIps = if (liveIps.isNotEmpty()) liveIps else ips.take(10)

            val results = withContext(Dispatchers.IO) {
                scanIps.chunked(hostConcurrency).map { chunk ->
                    chunk.map { ip ->
                        async {
                            val alive = PingUtil.ping(ip, 500) != null
                            if (!alive) return@async null

                            val mac = arpTable[ip]
                            val vendor = NetworkUtils.lookupMacVendor(mac)
                            val hostname = try {
                                kotlinx.coroutines.withTimeout(300) {
                                    java.net.InetAddress.getByName(ip).hostName
                                }.let { if (it != ip) it else null }
                            } catch (_: Exception) { null }

                            val services = scanDiscoverPorts(ip)
                            if (services.isNotEmpty()) {
                                HostInfo(ip = ip, hostname = hostname, macAddress = mac,
                                    macVendor = vendor, isAlive = true, openPorts = services)
                            } else null
                        }
                    }.awaitAll()
                }.flatten()
            }

            results.forEach { host ->
                completed++
                send(ScanEvent.Progress(host?.ip ?: subnet, completed.toInt(), total.toInt()))
                if (host != null) send(ScanEvent.HostFound(host))
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
