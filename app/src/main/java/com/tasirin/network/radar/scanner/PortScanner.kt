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
    }

    // Batasi total socket terbuka bersamaan (30 host x 50 port = 1500 socket > batas
    // file-descriptor Android ~1024 → "Too many open files" bikin port ke-skip diam-diam)
    private val socketPermits = Semaphore(500)

    fun scan(target: String, ports: IntArray = customPortsOverride ?: PortRangeParser.defaultPorts): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val hostConcurrency = if (isWide) 30 else 10
        val arpTable = NetworkUtils.readArpTable()
        var completed = 0L
        var found = 0
        val startMs = System.currentTimeMillis()

        emit(ScanEvent.Progress(
            "Port scan ${subnets.size} subnet — ${subnets.first()} … ${subnets.last()} (${total} IP)",
            0, total.toInt()))

        val totalSubnets = subnets.size
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"

            emit(ScanEvent.Progress("$subnetLabel — $subnet.0/24", completed.toInt(), total.toInt()))

            // Scan SEMUA IP — tanpa live-host filter agar tidak ada host yang ke-skip
            // (banyak perangkat tidak membalas ICMP tapi portnya terbuka)
            val scanIps = ips

            scanIps.chunked(hostConcurrency).forEach { chunk ->
                coroutineScope {
                    val deferreds = chunk.map { ip ->
                        async {
                            // Port scan dulu — DNS reverse lookup HANYA untuk host yang ketemu
                            // (DNS per-IP bikin subnet lambat/macet, padahal mayoritas mati)
                            val openPorts = scanHostPorts(ip, ports)
                            if (openPorts.isEmpty()) return@async (ip to null)

                            val hostname = try {
                                kotlinx.coroutines.withTimeout(300) {
                                    InetAddress.getByName(ip).hostName
                                }.let { if (it != ip) it else null }
                            } catch (_: Exception) { null }
                            val mac = arpTable[ip]
                            val vendor = NetworkUtils.lookupMacVendor(mac)
                            ip to HostInfo(ip = ip, hostname = hostname, macAddress = mac,
                                macVendor = vendor, isAlive = true, openPorts = openPorts)
                        }
                    }
                    deferreds.forEach { deferred ->
                        val (ip, host) = deferred.await()
                        completed++
                        if (host != null) found++
                        val elapsed = (System.currentTimeMillis() - startMs) / 1000
                        emit(ScanEvent.Progress("$subnetLabel · $ip · $found ditemukan · ${elapsed}s", completed.toInt(), total.toInt()))
                        if (host != null) emit(ScanEvent.HostFound(host))
                    }
                }
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PORT_SCAN, target = target)))
    }

    /** Scan satu host saja (dipakai untuk rescan per-host). */
    suspend fun scanHost(ip: String, ports: IntArray = customPortsOverride ?: PortRangeParser.defaultPorts): HostInfo? {
        val hostname = try {
            withTimeout(300) { InetAddress.getByName(ip).hostName }.let { if (it != ip) it else null }
        } catch (_: Exception) { null }
        val mac = NetworkUtils.readArpTable()[ip]
        val vendor = NetworkUtils.lookupMacVendor(mac)
        val openPorts = scanHostPorts(ip, ports)
        return if (openPorts.isNotEmpty()) HostInfo(ip = ip, hostname = hostname, macAddress = mac,
            macVendor = vendor, isAlive = true, openPorts = openPorts) else null
    }

    private suspend fun scanHostPorts(ip: String, ports: IntArray): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async { scanPort(ip, port) }
            }.mapNotNull { it.await() }
        }
    }

    private fun scanPort(ip: String, port: Int): PortInfo? {
        socketPermits.acquire()
        try {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(ip, port), 200)
                sock.soTimeout = 150
                var banner: String? = null
                try {
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    reader.use { r ->
                        var line: String?
                        val sb = StringBuilder()
                        for (i in 0 until 5) {
                            line = r.readLine() ?: break
                            sb.append(line).append(" ")
                        }
                        banner = sb.toString().trim().take(100)
                    }
                } catch (_: Exception) {}
                val service = detectService(port, banner)
                return PortInfo(port = port, service = service, banner = banner)
            } finally {
                try { sock.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            return null
        } finally {
            socketPermits.release()
        }
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
