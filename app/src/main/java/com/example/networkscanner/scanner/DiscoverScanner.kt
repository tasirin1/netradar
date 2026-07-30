package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import com.example.networkscanner.util.DebugLogger
import kotlinx.coroutines.flow.*

class DiscoverScanner {

    private val portScanner = PortScanner()
    private val cameraScanner = CameraScanner()
    private val routerScanner = RouterScanner()
    private val pingSweep = PingSweep()
    private val sharePorts = intArrayOf(21, 445, 139, 2049, 111, 135, 2049)
    fun scan(target: String): Flow<ScanEvent> = channelFlow {
        val ips = com.example.networkscanner.util.NetworkUtils.resolveTarget(target)
        val total = ips.size

        for ((idx, ip) in ips.withIndex()) {
            send(ScanEvent.Progress(ip, idx, total))

            val alive = com.example.networkscanner.util.PingUtil.ping(ip, 800) != null
            if (!alive) continue

            val services = scanDiscoverParallel(ip)

            if (services.isNotEmpty()) {
                send(ScanEvent.HostFound(HostInfo(
                    ip = ip, isAlive = true, openPorts = services
                )))
            }
        }

        send(ScanEvent.Complete(ScanResult(
            type = ScanType.DISCOVER, target = target
        )))
    }

    private suspend fun scanDiscoverParallel(ip: String): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            val cameraPorts = intArrayOf(80, 554, 34567, 37777, 37215)
            val routerPorts = intArrayOf(8291, 7547, 5000, 23, 22)
            val allPorts = (cameraPorts + routerPorts + sharePorts).distinct().toSet()

            allPorts.map { port ->
                async {
                    try {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), 200)
                        sock.close()
                        port
                    } catch (_: Exception) { null }
                }
            }.mapNotNull { it.await() }.mapNotNull { port ->
                detectDiscoverService(port)?.let { PortInfo(port, it) }
            }
        }
    }

    private fun detectDiscoverService(port: Int): String? = when (port) {
        80, 8080 -> "Camera Web"
        554 -> "RTSP Stream"
        34567 -> "Hikvision SDK"
        37777 -> "Dahua SDK"
        37215 -> "Hikvision Backdoor"
        8291 -> "Winbox (MikroTik)"
        7547 -> "TR-069"
        5000 -> "UPnP"
        23 -> "Telnet"
        22 -> "SSH"
        21 -> "FTP"
        445 -> "SMB (File Sharing)"
        139 -> "NetBIOS"
        2049 -> "NFS"
        111 -> "Portmapper"
        135 -> "MSRPC"
        else -> null
    }

    private fun detectCameraService(port: Int) = when (port) {
        80, 8080 -> "Camera Web"
        554 -> "RTSP Stream"
        34567 -> "Hikvision SDK"
        37777 -> "Dahua SDK"
        37215 -> "Hikvision Backdoor"
        else -> "Camera Service"
    }
}
