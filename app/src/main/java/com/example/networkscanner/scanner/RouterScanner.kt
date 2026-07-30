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
        val ips = NetworkUtils.autoExpandTarget(target)
        if (ips.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        // ARP pre-scan like v1.0
        var scanIps = ips.toList()
        val localIp = NetworkUtils.getLocalIp()
        val isLocal = localIp != null && ips.any { it.startsWith(localIp.substringBeforeLast(".")) }

        if (isLocal && ips.size > 1) {
            emit(ScanEvent.Progress("Discovering live hosts...", 0, ips.size))
            val subnet = ips.first().substringBeforeLast(".") + "."
            val live = NetworkUtils.arpScan(subnet)
            if (live.isNotEmpty()) {
                scanIps = ips.filter { it in live }
            }
        }

        val total = scanIps.size
        val arpTable = NetworkUtils.readArpTable()

        for ((idx, ip) in scanIps.withIndex()) {
            emit(ScanEvent.Progress(ip, idx, total))

            val mac = arpTable[ip]
            val vendor = NetworkUtils.lookupMacVendor(mac)

            val hostname = try {
                val hn = java.net.InetAddress.getByName(ip).hostName
                if (hn != ip) hn else null
            } catch (_: Exception) { null }

            val foundServices = scanRouterPorts(ip)
            if (foundServices.isNotEmpty()) {
                emit(ScanEvent.HostFound(HostInfo(
                    ip = ip, hostname = hostname, macAddress = mac, macVendor = vendor,
                    isAlive = true, openPorts = foundServices
                )))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.ROUTER, target = target)))
    }

    private suspend fun scanRouterPorts(ip: String): List<PortInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PortInfo>()
        for (port in routerPorts) {
            try {
                val found = probeRouter(ip, port)
                if (found != null) result.add(found)
            } catch (_: Exception) { }
        }
        result
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
