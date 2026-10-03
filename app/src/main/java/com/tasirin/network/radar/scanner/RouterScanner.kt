package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RouterScanner {

    // 161 (SNMP) & 1900 (SSDP) sengaja tak ada di sini: keduanya UDP-only
    // sehingga connect TCP tak pernah sukses — dicakup scan UDP.
    private val routerPorts = intArrayOf(
        80, 443, 8080, 8443, 8291, 7547, 5000,
        23, 22, 21, 2601, 2602
    )

    fun scan(target: String, speed: ScanSpeed = ScanSpeed.SEDANG): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val arpTable = NetworkUtils.readArpTable()
        val permits = Semaphore(speed.socketPermits)

        // Scan SEMUA IP — tanpa live-host filter agar tidak ada host yang ke-skip
        ScanLoop.scanSubnets(subnets, speed, "Router scan", scanOne = { ip ->
            val foundServices = scanRouterPorts(ip, speed.timeoutMs, permits)
            if (foundServices.isEmpty()) null
            else if (foundServices.none { it.service !in GENERIC_WEB }) null
            else ScanLoop.hostInfo(ip, arpTable, openPorts = foundServices)
        }) { ev -> emit(ev) }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.ROUTER, target = target)))
    }

    private suspend fun scanRouterPorts(ip: String, timeoutMs: Int, permits: Semaphore): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            routerPorts.map { port ->
                async { probeRouter(ip, port, timeoutMs, permits) }
            }.mapNotNull { it.await() }
        }
    }

    private suspend fun probeRouter(ip: String, port: Int, timeoutMs: Int, permits: Semaphore): PortInfo? = withContext(Dispatchers.IO) {
        // withPermit (suspend) agar antrean tidak memblokir thread IO.
        permits.withPermit {
            probeRouterLocked(ip, port, timeoutMs)
        }
    }

    private fun probeRouterLocked(ip: String, port: Int, timeoutMs: Int): PortInfo? {
        try {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
                sock.soTimeout = timeoutMs

                if (port in WEB_PORTS) {
                    val req = "GET / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val header = StringBuilder()
                    var line: String?
                    for (i in 0 until 25) {
                        line = reader.readLine() ?: break
                        if (line.isBlank()) break // akhir header: jangan tunggu keep-alive
                        header.append(line).append(" ")
                    }
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
                    return PortInfo(port, service)
                }

                val service = when (port) {
                    8291 -> "Winbox (MikroTik)"
                    7547 -> "TR-069 (ISP CWMP)"
                    5000 -> "UPnP Gateway"
                    23 -> "Telnet Router"
                    22 -> "SSH Router"
                    21 -> "FTP Router"
                    2601, 2602 -> "Quagga/FRRouting"
                    else -> null
                }
                return service?.let { PortInfo(port, it) }
            } finally {
                try { sock.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) { return null }
    }

    private companion object {
        val WEB_PORTS = setOf(80, 443, 8080, 8443)
        // Label generik: bukan bukti perangkat router.
        val GENERIC_WEB = setOf("Web Admin Panel", "Generic Web Server")
    }
}
