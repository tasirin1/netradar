package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore

class PortScanner {

    companion object {
        @Volatile
        var customPortsOverride: IntArray? = null
        val defaultPorts = intArrayOf(
            80, 443, 8080, 8443, 22, 23, 21, 53, 3389, 3306,
            8081, 8000, 3000, 5000, 8888, 9000, 81, 444, 5555, 5900,
            6379, 27017, 7547, 6666, 8291, 2000, 135, 139, 445, 1433,
            1521, 2049, 2375, 2376, 3128, 3307, 3388, 4444, 4848, 5432,
            6378, 7001, 8001, 8082, 8083, 8084, 8085, 8444, 9090, 9200
        )
    }

    fun scan(target: String, ports: IntArray = customPortsOverride ?: defaultPorts): Flow<ScanEvent> = flow {
        val ips = NetworkUtils.autoExpandTarget(target)
        if (ips.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        // Count unique /24 subnets
        val subnets = ips.map { it.substringBeforeLast(".") }.distinct()
        val isMultiSubnet = subnets.size > 1

        var scanIps = ips.toList()

        if (ips.size > 1) {
            emit(ScanEvent.Progress("Discovering live hosts (${subnets.size} subnet(s))...", 0, ips.size))
            val live = if (isMultiSubnet && subnets.size <= 256) {
                // Wide scan across multiple subnets
                val basePrefix = subnets.first().substringBeforeLast(".")
                val thirdMin = subnets.map { it.substringAfterLast(".").toInt() }.minOrNull() ?: 0
                val thirdMax = subnets.map { it.substringAfterLast(".").toInt() }.maxOrNull() ?: 255
                NetworkUtils.filterLiveHostsWide(basePrefix, thirdMin..thirdMax).toSet()
            } else {
                // Single subnet scan (faster)
                val subnet = ips.first().substringBeforeLast(".") + "."
                NetworkUtils.arpScan(subnet)
            }
            if (live.isNotEmpty()) {
                scanIps = ips.filter { it in live }
                if (scanIps.isEmpty()) scanIps = ips.take(10) // fallback
                emit(ScanEvent.Progress("Found ${scanIps.size} live host(s)", 0, scanIps.size))
            }
        }

        val semaphore = Semaphore(30)
        var progressIdx = 0

        for ((idx, ip) in scanIps.withIndex()) {
            emit(ScanEvent.Progress(ip, idx, scanIps.size))
            val hostname = try {
                val hn = InetAddress.getByName(ip).hostName
                if (hn != ip) hn else null
            } catch (_: Exception) { null }

            val arpTable = NetworkUtils.readArpTable()
            val mac = arpTable[ip]
            val vendor = NetworkUtils.lookupMacVendor(mac)

            val openPorts = scanHostPorts(ip, ports, semaphore)
            if (openPorts.isNotEmpty()) {
                emit(ScanEvent.HostFound(HostInfo(
                    ip = ip, hostname = hostname, macAddress = mac, macVendor = vendor,
                    isAlive = true, openPorts = openPorts
                )))
            }
            progressIdx++
        }
        emit(ScanEvent.Complete(ScanResult(type = ScanType.PORT_SCAN, target = target)))
    }

    private suspend fun scanHostPorts(ip: String, ports: IntArray, semaphore: Semaphore): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async {
                    semaphore.acquire()
                    try { scanPort(ip, port) } finally { semaphore.release() }
                }
            }.mapNotNull { it.await() }
        }
    }

    private fun scanPort(ip: String, port: Int): PortInfo? {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), 200)
            sock.soTimeout = 150
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
            val service = detectService(port, banner)
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
            80 -> "HTTP"; 443 -> "HTTPS"; 22 -> "SSH"; 21 -> "FTP"
            23 -> "Telnet"; 53 -> "DNS"; 3389 -> "RDP"; 3306 -> "MySQL"
            3307 -> "MySQL Alt"; 8080 -> "HTTP-Alt"; 8443 -> "HTTPS-Alt"
            5432 -> "PostgreSQL"; 6379 -> "Redis"; 27017 -> "MongoDB"
            1433 -> "MSSQL"; 1521 -> "Oracle DB"; 5900 -> "VNC"
            445 -> "SMB"; 139 -> "NetBIOS"; 135 -> "MSRPC"; 2049 -> "NFS"
            111 -> "Portmapper"; 8291 -> "Winbox (MikroTik)"
            7547 -> "TR-069"; 5000 -> "UPnP"; 5555 -> "ADB"
            2375 -> "Docker"; 2376 -> "Docker TLS"; 3128 -> "Squid"
            8444 -> "HTTPS-Alt"; 9090 -> "HTTP-Alt"
            3000 -> "HTTP-Dev"; 8000 -> "HTTP-Alt"; 8888 -> "HTTP-Dev"
            9000 -> "HTTP-Dev"; 81 -> "HTTP-Alt"; 444 -> "HTTPS-Alt"
            else -> null
        }
    }
}
