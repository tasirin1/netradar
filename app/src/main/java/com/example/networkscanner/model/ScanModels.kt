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

enum class ScanType(val label: String) {
    PORT_SCAN("Port Scan"),
    CAMERA("CCTV"),
    ROUTER("Router"),
    URL_PATH("URL Path"),
    DISCOVER("Discover"),
    PING("Ping Sweep")
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

sealed class ScanEvent {
    data class Progress(val ip: String, val current: Int, val total: Int) : ScanEvent()
    data class HostFound(val host: HostInfo) : ScanEvent()
    data class UrlFound(val url: UrlDiscovery) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
    data class Complete(val result: ScanResult) : ScanEvent()
}
