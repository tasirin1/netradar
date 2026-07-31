package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class RouterScanner {

    private val routerPorts = intArrayOf(
        80, 443, 8080, 8443, 8291, 7547, 5000,
        23, 22, 21, 161, 2601, 2602, 1900
    )

    fun scan(target: String): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val hostConcurrency = if (isWide) 20 else 5
        val arpTable = NetworkUtils.readArpTable()
        var completed = 0L

        emit(ScanEvent.Progress("Router scan ${subnets.size} subnet(s), ${total} IP(s)...", 0, total.toInt()))

        subnets.forEach { subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            // Scan SEMUA IP — tanpa live-host filter agar tidak ada host yang ke-skip
            // (banyak perangkat tidak membalas ICMP tapi portnya terbuka)
            val scanIps = ips

            val results = withContext(Dispatchers.IO) {
                scanIps.chunked(hostConcurrency).map { chunk ->
                    chunk.map { ip ->
                        async {
                            val mac = arpTable[ip]
                            val vendor = NetworkUtils.lookupMacVendor(mac)
                            val hostname = try {
                                kotlinx.coroutines.withTimeout(300) {
                                    java.net.InetAddress.getByName(ip).hostName
                                }.let { if (it != ip) it else null }
                            } catch (_: Exception) { null }
                            val foundServices = scanRouterPorts(ip)
                            if (foundServices.isNotEmpty()) {
                                HostInfo(ip = ip, hostname = hostname, macAddress = mac,
                                    macVendor = vendor, isAlive = true, openPorts = foundServices)
                            } else null
                        }
                    }.awaitAll()
                }.flatten()
            }

            results.forEach { host ->
                completed++
                emit(ScanEvent.Progress(host?.ip ?: subnet, completed.toInt(), total.toInt()))
                if (host != null) emit(ScanEvent.HostFound(host))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.ROUTER, target = target)))
    }

    private suspend fun scanRouterPorts(ip: String): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            routerPorts.map { port ->
                async { probeRouter(ip, port) }
            }.mapNotNull { it.await() }
        }
    }

    private suspend fun probeRouter(ip: String, port: Int): PortInfo? = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), 300)
            sock.soTimeout = 300

            if (port in listOf(80, 443, 8080, 8443)) {
                try {
                    val req = "GET / HTTP/1.1\r\nHost: $ip\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val header = StringBuilder()
                    var line: String?
                    for (i in 0 until 25) {
                        line = reader.readLine() ?: break
                        header.append(line).append(" ")
                    }
                    sock.close()
                    val h = header.toString().lowercase()
                    val service = when {
                        h.contains("mikrotik") || h.contains("routeros") -> "MikroTik RouterOS"
                        h.contains("dd-wrt") || h.contains("ddwrt") -> "DD-WRT Router"
                        h.contains("openwrt") -> "OpenWrt Router"
                        h.contains("tomato") -> "Tomato Router"
                        h.contains("asus") -> "ASUS Router"
                        h.contains("tplink") || h.contains("tp-link") -> "TP-Link Router"
                        h.contains("dlink") || h.contains("d-link") -> "D-Link Router"
                        h.contains("netgear") -> "Netgear Router"
                        h.contains("cisco") -> "Cisco Router"
                        h.contains("huawei") -> "Huawei Router"
                        h.contains("ubiquiti") || h.contains("unifi") -> "Ubiquiti Router"
                        h.contains("zyxel") -> "Zyxel Router"
                        h.contains("tenda") -> "Tenda Router"
                        h.contains("mercury") -> "Mercury Router"
                        h.contains("totolink") -> "TOTOLINK Router"
                        h.contains("phicomm") -> "Phicomm Router"
                        h.contains("ruijie") -> "Ruijie Router"
                        h.contains("linksys") -> "Linksys Router"
                        h.contains("belkin") -> "Belkin Router"
                        h.contains("buffalo") -> "Buffalo Router"
                        h.contains("vigor") -> "DrayTek Vigor Router"
                        h.contains("fritz") -> "AVM Fritz!Box"
                        h.contains("apache") || h.contains("nginx") || h.contains("iis") -> "Generic Web Server"
                        else -> "Web Admin Panel"
                    }
                    return@withContext PortInfo(port, service)
                } catch (_: Exception) {
                    sock.close()
                    return@withContext null
                }
            }

            sock.close()
            val service = when (port) {
                8291 -> "Winbox (MikroTik)"
                7547 -> "TR-069 (ISP CWMP)"
                5000 -> "UPnP Gateway"
                23 -> "Telnet Router"
                22 -> "SSH Router"
                21 -> "FTP Router"
                161 -> "SNMP Router"
                2601, 2602 -> "Quagga/FRRouting"
                1900 -> "UPnP SSDP"
                else -> null
            }
            return@withContext service?.let { PortInfo(port, it) }
        } catch (_: Exception) { null }
    }
}
