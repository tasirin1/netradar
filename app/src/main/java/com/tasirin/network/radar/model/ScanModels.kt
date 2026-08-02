package com.tasirin.network.radar.model

data class PortInfo(
    val port: Int,
    val service: String? = null,
    val banner: String? = null
)

data class HostInfo(
    val ip: String,
    val hostname: String? = null,
    val label: String? = null,
    val macAddress: String? = null,
    val macVendor: String? = null,
    val latencyMs: Long? = null,
    val osGuess: String? = null,
    val isAlive: Boolean = true,
    val openPorts: List<PortInfo> = emptyList(),
    val isNew: Boolean = false,
    val ipConflict: Boolean = false
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
    UDP("UDP"),
    TRACE("Traceroute"),
    MONITOR("Monitor")
}

enum class SortMode(val label: String) {
    IP("IP"),
    PORTS("Ports"),
    LATENCY("Latency"),
    NAMA("Nama"),
    UPTIME("Uptime")
}

/** Filter tampilan berdasarkan status terakhir (online/offline). */
enum class HostStatusFilter(val label: String) {
    ALL("Semua"),
    ONLINE("Online"),
    OFFLINE("Offline")
}

/** Jenis perangkat yang terdeteksi dari service/port yang terbuka. */
enum class DeviceKind(val icon: String) {
    CAMERA("📷"), ROUTER("🌐"), SHARE("📁"),
    PRINTER("🖨️"), NAS("💾"), TV("📺"), IOT("💡"), PHONE("📱")
}

/** Filter tampilan hasil host. */
enum class DeviceFilter(val label: String) {
    ALL("Semua"),
    CAMERA("📷 CCTV"),
    ROUTER("🌐 Router"),
    SHARE("📁 Share"),
    PRINTER("🖨️ Printer"),
    NAS("💾 NAS"),
    TV("📺 TV"),
    IOT("💡 IoT"),
    PHONE("📱 HP")
}

fun HostInfo.deviceKinds(): Set<DeviceKind> = buildSet {
    if (openPorts.any { p ->
            p.service?.contains("Camera", true) == true || p.service?.contains("Hikvision", true) == true ||
            p.service?.contains("Dahua", true) == true || p.service?.contains("ONVIF", true) == true ||
            p.service?.contains("RTSP", true) == true }) add(DeviceKind.CAMERA)
    if (openPorts.any { p ->
            p.service?.contains("Router", true) == true || p.service?.contains("MikroTik", true) == true ||
            p.service?.contains("Winbox", true) == true || p.service?.contains("TR-069", true) == true ||
            p.service?.contains("UPnP", true) == true }) add(DeviceKind.ROUTER)
    if (openPorts.any { p -> p.port in listOf(445, 139, 2049, 21, 111, 135) }) add(DeviceKind.SHARE)
    if (openPorts.any { p ->
            p.port in PRINTER_PORTS ||
            p.service?.contains("printer", true) == true ||
            p.service?.contains("ipp", true) == true ||
            p.service?.contains("jetdirect", true) == true }) add(DeviceKind.PRINTER)
    if (openPorts.any { p -> p.port in NAS_PORTS }) add(DeviceKind.NAS)
    if (openPorts.any { p ->
            p.port in TV_PORTS ||
            p.service?.contains("dlna", true) == true ||
            p.service?.contains("media", true) == true ||
            p.service?.contains("smart tv", true) == true }) add(DeviceKind.TV)
    if (openPorts.any { p -> p.port in IOT_PORTS }) add(DeviceKind.IOT)
    if (openPorts.any { p -> p.port in PHONE_PORTS }) add(DeviceKind.PHONE)
}

private val PRINTER_PORTS = setOf(515, 631, 9100)
private val NAS_PORTS = setOf(548, 873, 3260)
private val TV_PORTS = setOf(55000, 9197, 8060, 56789, 1926, 49000)
private val IOT_PORTS = setOf(1883, 8883, 5683, 502, 623, 2323)
private val PHONE_PORTS = setOf(5555, 4747)

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
    val durationMs: Long = 0
)

/** Satu entri riwayat scan (target, waktu, hasil) yang disimpan antar sesi. */
data class ScanHistoryEntry(
    val time: Long,
    val type: String,
    val target: String,
    val hostCount: Int,
    val portCount: Int,
    val durationMs: Long
)

/** Status monitor semua host: ip -> online/offline. */
data class MonitorState(
    val isRunning: Boolean = false,
    val statuses: Map<String, Boolean> = emptyMap(),
    val pings: Int = 0
)

/** Satu titik riwayat ketersediaan perangkat. */
data class UptimeEvent(
    val ts: Long,
    val online: Boolean
)

/** Satu titik riwayat ping (latency) perangkat. */
data class PingEvent(
    val ts: Long,
    val latencyMs: Long
)

/** Hasil perbandingan antara scan sebelumnya dan scan terakhir. */
data class ScanDiff(
    val added: List<HostInfo> = emptyList(),
    val removed: List<HostInfo> = emptyList(),
    val changed: List<HostInfo> = emptyList()
)

sealed class ScanEvent {
    data class Progress(val ip: String, val current: Int, val total: Int) : ScanEvent()
    data class HostFound(val host: HostInfo) : ScanEvent()
    data class UrlFound(val url: UrlDiscovery) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
    data class Complete(val result: ScanResult) : ScanEvent()
}

/**
 * Level sensitivitas scan: mengatur host paralel, timeout koneksi, dan batas socket.
 * Sangat Stabil = paling jinak ke jaringan (paling sedikit skip, paling lambat);
 * Cepat = paling agresif (lebih cepat, risiko skip saat jaringan padat).
 * portCount = jumlah port umum yang discan: makin sensitif level, makin banyak port.
 */
enum class ScanSpeed(
    val label: String,
    val hostWide: Int,
    val hostLocal: Int,
    val timeoutMs: Int,
    val socketPermits: Int,
    val portCount: Int
) {
    SANGAT_STABIL("Sangat Stabil", 8, 3, 600, 64, 70),
    STABIL("Stabil", 15, 5, 400, 150, 40),
    SEDANG("Sedang", 30, 10, 200, 400, 28),
    CEPAT("Cepat", 50, 15, 150, 500, 16)
}

/** Daftar port umum, diurutkan dari yang paling sering terbuka. */
object PortRangeParser {
    val defaultPorts = intArrayOf(
        80, 443, 8080, 8443, 22, 23, 21, 53, 3389, 3306,
        139, 445, 135, 554, 8000, 5000,
        8888, 9000, 3000, 5432, 6379, 27017, 8081, 81, 5555, 5900, 7547, 6666,
        8291, 2000, 1433, 1521, 2049, 2375, 2376, 3128, 3307, 3388, 4444, 4848,
        25, 110, 143, 161, 162, 2323, 5060, 1723, 10000, 1434,
        6378, 7001, 8001, 8082, 8083, 8084, 8085, 8444, 9090, 9200,
        515, 631, 9100, 548, 873, 3260, 1883, 8883, 5683, 502
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
