package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
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
        var completed = 0

        for (ip in ips) {
            send(ScanEvent.Progress(ip, completed, total))

            // Ping check
            val alive = com.example.networkscanner.util.PingUtil.ping(ip, 800) != null
            if (!alive) {
                completed++
                continue
            }

            val services = mutableListOf<PortInfo>()

            // Quick camera probes
            for (port in intArrayOf(80, 554, 34567, 37777, 37215)) {
                try {
                    withTimeout(300) {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), 200)
                        sock.close()
                        services.add(PortInfo(port, detectCameraService(port)))
                    }
                } catch (_: Exception) {}
            }

            // Router-specific ports
            for (port in intArrayOf(8291, 7547, 5000, 23, 22)) {
                try {
                    withTimeout(300) {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), 200)
                        sock.close()
                        val svc = when (port) {
                            8291 -> "Winbox (MikroTik)"
                            7547 -> "TR-069"
                            5000 -> "UPnP"
                            23 -> "Telnet"
                            22 -> "SSH"
                            else -> null
                        }
                        svc?.let { services.add(PortInfo(port, it)) }
                    }
                } catch (_: Exception) {}
            }

            // File share ports
            for (port in sharePorts) {
                try {
                    withTimeout(300) {
                        val sock = java.net.Socket()
                        sock.connect(java.net.InetSocketAddress(ip, port), 200)
                        sock.close()
                        val svc = when (port) {
                            21 -> "FTP"
                            445 -> "SMB (File Sharing)"
                            139 -> "NetBIOS"
                            2049 -> "NFS"
                            111 -> "Portmapper"
                            135 -> "MSRPC"
                            else -> null
                        }
                        svc?.let { services.add(PortInfo(port, it)) }
                    }
                } catch (_: Exception) {}
            }

            if (services.isNotEmpty()) {
                send(ScanEvent.HostFound(HostInfo(
                    ip = ip, isAlive = true, openPorts = services
                )))
            }

            completed++
        }

        send(ScanEvent.Complete(ScanResult(
            type = ScanType.DISCOVER, target = target
        )))
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
