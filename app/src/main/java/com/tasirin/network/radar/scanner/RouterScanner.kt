package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore

class RouterScanner {

    private val routerPorts = intArrayOf(
        80, 443, 8080, 8443, 8291, 7547, 5000,
        23, 22, 21, 161, 2601, 2602, 1900
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
            if (foundServices.isEmpty()) null else ScanLoop.hostInfo(ip, arpTable, openPorts = foundServices)
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
        permits.acquire()
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), timeoutMs)
            sock.soTimeout = timeoutMs

            if (port in WEB_PORTS) {
                try {
                    val req = "GET / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n"
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
        finally {
            permits.release()
        }
    }

    private companion object {
        val WEB_PORTS = setOf(80, 443, 8080, 8443)
    }
}
