package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class PortScanner {

    private val commonPorts = intArrayOf(
        80, 443, 8080, 8443, 22, 23, 21, 53, 3389, 3306,
        8081, 8000, 3000, 5000, 8888, 9000, 81, 444, 5555, 5900,
        6379, 27017, 7547, 6666, 8291, 2000, 135, 139, 445, 1433,
        1521, 2049, 2375, 2376, 3128, 3307, 3388, 4444, 4848, 5432,
        6378, 7001, 8001, 8082, 8083, 8084, 8085, 8444, 9090, 9200
    )

    fun scan(target: String, ports: IntArray = commonPorts): Flow<ScanEvent> = flow {
        val ips = resolveTargets(target)
        val totalTargets = ips.size

        for ((ipIdx, ip) in ips.withIndex()) {
            emit(ScanEvent.Progress(ip, ipIdx, totalTargets))

            // Scan ports in parallel for this IP (like v1.0 did with thread pool)
            val openPorts = scanPortsParallel(ip, ports)

            if (openPorts.isNotEmpty()) {
                val host = HostInfo(
                    ip = ip,
                    isAlive = true,
                    openPorts = openPorts
                )
                emit(ScanEvent.HostFound(host))
            }
        }

        emit(ScanEvent.Complete(ScanResult(
            type = ScanType.PORT_SCAN, target = target
        )))
    }

    // Parallel port scan — mimics v1.0's Executors.newFixedThreadPool(255)
    private suspend fun scanPortsParallel(ip: String, ports: IntArray): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async {
                    probePort(ip, port)
                }
            }.mapNotNull { it.await() }
        }
    }

    private fun probePort(ip: String, port: Int): PortInfo? {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), 200)
            sock.soTimeout = 150
            var service: String? = null
            var banner: String? = null

            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                reader.use {
                    var line: String?
                    val sb = StringBuilder()
                    for (i in 0 until 5) {
                        line = reader.readLine() ?: break
                        sb.append(line).append(" ")
                    }
                    banner = sb.toString().trim().take(100)
                }
            } catch (_: Exception) {}

            sock.close()
            service = detectService(port, banner)
            PortInfo(port = port, service = service, banner = banner)
        } catch (_: Exception) { null }
    }

    private fun detectService(port: Int, banner: String?): String? {
        if (banner != null) {
            val b = banner.lowercase()
            when {
                b.contains("ssh") -> return "SSH"
                b.contains("ftp") -> return "FTP"
                b.contains("http") -> return when {
                    b.contains("nginx") -> "Nginx"
                    b.contains("apache") -> "Apache"
                    b.contains("iis") -> "IIS"
                    b.contains("lighttpd") -> "Lighttpd"
                    else -> "HTTP"
                }
                b.contains("smtp") -> return "SMTP"
                b.contains("mysql") -> return "MySQL"
                b.contains("mikrotik") || b.contains("routeros") -> return "MikroTik RouterOS"
                b.contains("hikvision") -> return "Hikvision"
                b.contains("dahua") -> return "Dahua"
            }
        }
        return when (port) {
            80 -> "HTTP"
            443 -> "HTTPS"
            22 -> "SSH"
            21 -> "FTP"
            23 -> "Telnet"
            53 -> "DNS"
            3389 -> "RDP"
            3306 -> "MySQL"
            3307 -> "MySQL Alt"
            8080 -> "HTTP-Alt"
            8443 -> "HTTPS-Alt"
            5432 -> "PostgreSQL"
            6379 -> "Redis"
            27017 -> "MongoDB"
            1433 -> "MSSQL"
            1521 -> "Oracle DB"
            5900 -> "VNC"
            445 -> "SMB"
            139 -> "NetBIOS"
            135 -> "MSRPC"
            2049 -> "NFS"
            111 -> "Portmapper"
            8291 -> "Winbox (MikroTik)"
            7547 -> "TR-069"
            5000 -> "UPnP"
            5555 -> "ADB"
            2375, 2376 -> "Docker"
            3128 -> "Squid Proxy"
            8444 -> "HTTPS-Alt"
            9090 -> "HTTP-Alt"
            3000, 8000, 8888, 9000 -> "HTTP-Dev"
            else -> null
        }
    }
    private fun resolveTargets(input: String): List<String> = com.example.networkscanner.util.NetworkUtils.autoExpandTarget(input)
}
