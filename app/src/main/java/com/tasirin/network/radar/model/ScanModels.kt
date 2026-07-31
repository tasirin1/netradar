package com.tasirin.network.radar.model

data class ScanTarget(
    val raw: String,
    val resolvedIps: List<String> = emptyList(),
    val isCidr: Boolean = false
)

data class PortInfo(
    val port: Int,
    val service: String? = null,
    val banner: String? = null,
    val isOpen: Boolean = true
)

data class HostInfo(
    val ip: String,
    val hostname: String? = null,
    val macAddress: String? = null,
    val macVendor: String? = null,
    val latencyMs: Long? = null,
    val isAlive: Boolean = true,
    val openPorts: List<PortInfo> = emptyList(),
    val services: List<ServiceInfo> = emptyList()
)

data class ServiceInfo(
    val port: Int,
    val name: String,
    val version: String? = null,
    val details: String? = null
)

data class NetworkInfo(
    val localIp: String = "",
    val gateway: String = "",
    val subnet: String = "",
    val availableInterfaces: List<NetworkInterfaceInfo> = emptyList(),
    val selectedInterface: String = ""
)

data class NetworkInterfaceInfo(
    val name: String,
    val ip: String,
    val isActive: Boolean = false
)

enum class ScanType(val label: String) {
    PORT_SCAN("Port Scan"),
    CAMERA("CCTV"),
    ROUTER("Router"),
    URL_PATH("URL Path"),
    DISCOVER("Discover"),
    PING("Ping Sweep"),
    MONITOR("Monitor")
}

enum class SortMode(val label: String) {
    IP("IP"),
    PORTS("Ports"),
    LATENCY("Latency"),
    HOSTNAME("Hostname")
}

data class ScanResult(
    val type: ScanType,
    val target: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hosts: List<HostInfo> = emptyList(),
    val discoveredUrls: List<UrlDiscovery> = emptyList(),
    val summary: ScanSummary = ScanSummary()
)

data class UrlDiscovery(
    val url: String,
    val statusCode: Int,
    val title: String? = null
)

data class ScanSummary(
    val totalHosts: Int = 0,
    val aliveHosts: Int = 0,
    val openPorts: Int = 0,
    val camerasFound: Int = 0,
    val routersFound: Int = 0,
    val sharesFound: Int = 0,
    val urlsFound: Int = 0,
    val durationMs: Long = 0
)

data class PingMonitorState(
    val ip: String = "",
    val isRunning: Boolean = false,
    val lastLatency: Long? = null,
    val history: List<PingResult> = emptyList()
)

data class PingResult(
    val timestamp: Long,
    val latencyMs: Long?,
    val isAlive: Boolean
)

sealed class ScanEvent {
    data class Progress(val ip: String, val current: Int, val total: Int) : ScanEvent()
    data class HostFound(val host: HostInfo) : ScanEvent()
    data class UrlFound(val url: UrlDiscovery) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
    data class Complete(val result: ScanResult) : ScanEvent()
    data class PingUpdate(val result: PingResult) : ScanEvent()
}

object PortRangeParser {
    /**
     * Parse port string like "80,443,8080" or "1-1000" or "80,443,3000-4000"
     * Returns list of ports or default list if input is empty.
     */
    fun parse(input: String): IntArray {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return defaultPorts

        val ports = mutableSetOf<Int>()
        val parts = trimmed.split(",").map { it.trim() }
        for (part in parts) {
            if (part.contains("-")) {
                val range = part.split("-").map { it.trim().toIntOrNull() }
                if (range.size == 2 && range[0] != null && range[1] != null) {
                    val start = range[0]!!.coerceIn(1, 65535)
                    val end = range[1]!!.coerceIn(start, 65535)
                    (start..end).forEach { ports.add(it) }
                }
            } else {
                val p = part.toIntOrNull()
                if (p != null && p in 1..65535) ports.add(p)
            }
        }
        return if (ports.isEmpty()) defaultPorts else ports.toIntArray()
    }

    val defaultPorts = intArrayOf(
        80, 443, 8080, 8443, 22, 23, 21, 53, 3389, 3306,
        8081, 8000, 3000, 5000, 8888, 9000, 81, 444, 5555, 5900,
        6379, 27017, 7547, 6666, 8291, 2000, 135, 139, 445, 1433,
        1521, 2049, 2375, 2376, 3128, 3307, 3388, 4444, 4848, 5432,
        6378, 7001, 8001, 8082, 8083, 8084, 8085, 8444, 9090, 9200
    )
}

object PortDescriptions {
    val map = mapOf(
        20 to "FTP Data",
        21 to "FTP - File Transfer",
        22 to "SSH - Secure Shell",
        23 to "Telnet - Remote Access",
        25 to "SMTP - Email Send",
        53 to "DNS - Domain Name",
        69 to "TFTP - Trivial FTP",
        80 to "HTTP - Web Server",
        110 to "POP3 - Email Receive",
        111 to "RPC - Portmapper",
        123 to "NTP - Network Time",
        135 to "MSRPC - Windows RPC",
        137 to "NetBIOS Name",
        138 to "NetBIOS Datagram",
        139 to "NetBIOS Session",
        143 to "IMAP - Email",
        161 to "SNMP - Network Monitor",
        162 to "SNMP Trap",
        179 to "BGP - Routing",
        389 to "LDAP - Directory",
        443 to "HTTPS - Secure Web",
        445 to "SMB - File Sharing",
        464 to "Kerberos Passwd",
        465 to "SMTPS - Secure Email",
        500 to "IPsec - VPN",
        502 to "Modbus - SCADA",
        514 to "Syslog",
        520 to "RIP - Routing",
        521 to "RIPng - Routing",
        524 to "NCP - NetWare",
        543 to "Kerberos Login",
        544 to "Kerberos Shell",
        546 to "DHCPv6 Client",
        547 to "DHCPv6 Server",
        548 to "AFP - File Sharing",
        554 to "RTSP - Streaming",
        560 to "RMonitor",
        563 to "NNTP over TLS",
        585 to "IMAP over TLS",
        587 to "SMTP Submission",
        623 to "IPMI - Remote Mgmt",
        631 to "IPP - Printing",
        636 to "LDAPS - Secure LDAP",
        639 to "MSDP - Multicast",
        646 to "LDP - Label Dist.",
        666 to "Doom - Game",
        691 to "MS Exchange Routing",
        694 to "Linux HA",
        698 to "OLSR - Routing",
        749 to "Kerberos Admin",
        750 to "Kerberos IV",
        751 to "Kerberos IV Master",
        8291 to "Winbox - MikroTik",
        8443 to "HTTPS Alt",
        8444 to "HTTPS Alt",
        8888 to "HTTP Alt / Game",
        9000 to "HTTP Alt",
        9090 to "HTTP Alt",
        3306 to "MySQL - Database",
        3307 to "MySQL Alternative",
        3389 to "RDP - Remote Desktop",
        5432 to "PostgreSQL - Database",
        5555 to "ADB - Android Debug",
        5900 to "VNC - Remote Desktop",
        6379 to "Redis - Cache",
        6378 to "Redis Alternative",
        6666 to "IRC / Custom",
        7001 to "WebLogic Admin",
        7547 to "TR-069 - ISP Mgmt",
        8000 to "HTTP Alt",
        8001 to "HTTP Alt",
        8080 to "HTTP Proxy/Alt",
        8081 to "HTTP Alt",
        8082 to "HTTP Alt",
        8443 to "HTTPS Alt",
        27017 to "MongoDB - Database",
        34567 to "Hikvision SDK",
        37215 to "Hikvision Backdoor",
        37777 to "Dahua SDK",
        4899 to "RAdmin - Remote",
        5000 to "UPnP / Docker",
        11211 to "Memcached - Cache",
        11215 to "Memcached SSL",
        25565 to "Minecraft Server",
        32400 to "Plex Media Server",
        33434 to "traceroute"
    )

    fun get(port: Int): String? = map[port]
}
