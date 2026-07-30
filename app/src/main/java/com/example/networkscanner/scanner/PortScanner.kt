package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import kotlin.math.min

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
        val totalTargets = ips.size * ports.size
        var completed = 0

        for (ip in ips) {
            val hostOpenPorts = mutableListOf<PortInfo>()

            for (port in ports) {
                emit(ScanEvent.Progress(ip, completed, totalTargets))
                val result = probePort(ip, port)
                if (result != null) {
                    hostOpenPorts.add(result)
                }
                completed++
                // Small delay to avoid flooding network
                delay(5)
            }

            if (hostOpenPorts.isNotEmpty()) {
                val host = HostInfo(
                    ip = ip,
                    isAlive = true,
                    openPorts = hostOpenPorts
                )
                emit(ScanEvent.HostFound(host))
            }
        }

        emit(ScanEvent.Complete(ScanResult(
            type = ScanType.PORT_SCAN,
            target = target,
            hosts = emptyList(), // Results collected in ViewModel
            summary = ScanSummary(totalHosts = ips.size)
        )))
    }

    private suspend fun probePort(ip: String, port: Int): PortInfo? = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), 200)
            sock.soTimeout = 200
            var service: String? = null
            var banner: String? = null

            // Try to read banner
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

    private fun resolveTargets(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 24
            return expandCidr(parts[0], prefix)
        }
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-")
            val base = parts[0].trim()
            val baseParts = base.split(".")
            if (baseParts.size == 4) {
                val start = baseParts[3].toIntOrNull() ?: return listOf(trimmed)
                val end = parts[1].trim().toIntOrNull() ?: return listOf(trimmed)
                val prefix = baseParts.dropLast(1).joinToString(".")
                return (start..end).map { "$prefix.$it" }
            }
        }
        return listOf(trimmed)
    }

    private fun expandCidr(baseIp: String, prefix: Int): List<String> {
        return try {
            if (prefix < 8 || prefix > 30) return listOf(baseIp)
            val addr = InetAddress.getByName(baseIp)
            val bytes = addr.address
            val ipInt = ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            val masked = ipInt and mask
            val count = 1 shl (32 - prefix)
            if (count > 65536 || count <= 2) listOf(baseIp)
            else (1 until count - 1).map {
                val ip = masked + it
                "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
            }
        } catch (_: Exception) { listOf(baseIp) }
    }
}
