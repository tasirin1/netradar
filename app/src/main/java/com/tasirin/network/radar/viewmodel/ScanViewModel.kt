package com.tasirin.network.radar.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.scanner.ScannerManager
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import com.tasirin.network.radar.util.ResultsStore
import com.tasirin.network.radar.util.WakeOnLan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScanUiState(
    val target: String = "",
    val customPorts: String = "",
    val selectedProfile: PortProfile = PortProfile.DEFAULT,
    val showCustomPorts: Boolean = false,
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val scanType: ScanType? = null,
    val progress: String = "",
    val progressPercent: Float = 0f,
    val hosts: List<HostInfo> = emptyList(),
    val discoveredUrls: List<UrlDiscovery> = emptyList(),
    val summary: String = "Ready",
    val summaryColor: Long = 0xFF00695C,
    val isSummaryOk: Boolean = true,
    val error: String? = null,
    val scanResult: ScanResult? = null,
    val hostSummary: String = "",
    val isDarkTheme: Boolean? = null,
    val showAbout: Boolean = false,
    val networkInfo: NetworkInfo = NetworkInfo(),
    val sortMode: SortMode = SortMode.IP,
    val monitor: PingMonitorState = PingMonitorState(),
    val copyFeedback: String? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = ScannerManager()
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _hosts = linkedMapOf<String, HostInfo>()
    private val _urls = linkedMapOf<String, UrlDiscovery>()
    private var _startTime = 0L
    private var monitorJob: Job? = null
    private var lastPersistAt = 0L

    init {
        refreshNetworkInfo()
        // Muat hasil dari sesi sebelumnya agar tidak hilang saat app ditutup
        val (hosts, urls) = ResultsStore.load(getApplication())
        hosts.forEach { _hosts[it.ip] = it }
        urls.forEach { _urls[it.url] = it }
        _state.update {
            it.copy(
                hosts = _hosts.values.toList(),
                discoveredUrls = _urls.values.toList(),
                summary = if (_hosts.isEmpty()) "Ready" else "Riwayat: ${_hosts.size} host(s), ${_urls.size} URL(s)"
            )
        }
    }

    fun refreshNetworkInfo() {
        val localIp = NetworkUtils.getLocalIp() ?: ""
        val gateway = NetworkUtils.getLocalGateway() ?: ""
        val prefix = NetworkUtils.getLocalNetworkPrefix()
        val subnet = if (prefix != null) "$prefix.0/24" else ""
        val interfaces = NetworkUtils.getAvailableInterfaces()
        val selected = NetworkUtils.selectedInterfaceName
        _state.update {
            it.copy(networkInfo = NetworkInfo(localIp, gateway, subnet, interfaces, selected))
        }
    }

    fun selectInterface(name: String) {
        NetworkUtils.selectedInterfaceName = name
        refreshNetworkInfo()
    }

    fun setTarget(target: String) { _state.update { it.copy(target = target) } }
    fun setCustomPorts(ports: String) { _state.update { it.copy(customPorts = ports) } }
    fun setPortProfile(profile: PortProfile) { _state.update { it.copy(selectedProfile = profile) } }
    fun toggleCustomPorts() { _state.update { it.copy(showCustomPorts = !it.showCustomPorts) } }
    fun toggleAbout() { _state.update { it.copy(showAbout = !it.showAbout) } }

    fun startScan(type: ScanType) {
        var target = _state.value.target.trim()
        if (target.isEmpty()) {
            val subnet = NetworkUtils.getLocalSubnet()
            val localIp = NetworkUtils.getLocalIp()
            if (subnet != null && localIp != null) {
                target = localIp; _state.update { it.copy(target = target) }
            }
            if (target.isEmpty()) {
                _state.update { it.copy(error = "Enter target IP or URL") }; return
            }
        }

        if (type == ScanType.MONITOR) { startMonitor(target); return }

        _startTime = System.currentTimeMillis()
        _state.update {
            it.copy(isScanning = true, isPaused = false, scanType = type, error = null,
                summary = "${type.label} starting...",
                summaryColor = 0xFF00695C, isSummaryOk = true, progress = "", progressPercent = 0f,
                hostSummary = "", scanResult = null)
        }

        viewModelScope.launch {
            try {
                scannerManager.scan(type, target).collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> {
                            val pct = if (event.total > 0) event.current.toFloat() / event.total else 0f
                            val pctText = if (event.total > 0) " (${event.current}/${event.total})" else ""
                            _state.update { it.copy(progress = "Scanning ${event.ip}$pctText", progressPercent = pct) }
                        }
                        is ScanEvent.HostFound -> {
                            _hosts[event.host.ip] = event.host
                            if (_hosts.size <= 500) applySort()
                            else _state.update { it.copy(hosts = _hosts.values.toList()) }
                            persistResults()
                        }
                        is ScanEvent.UrlFound -> {
                            _urls[event.url.url] = event.url
                            _state.update { it.copy(discoveredUrls = _urls.values.toList()) }
                            persistResults()
                        }
                        is ScanEvent.Error -> { _state.update { it.copy(error = event.message) } }
                        is ScanEvent.Complete -> {
                            val duration = System.currentTimeMillis() - _startTime
                            applySort()
                            persistResults(force = true)
                            val result = event.result.copy(hosts = _hosts.values.toList(),
                                discoveredUrls = _urls.values.toList(),
                                summary = ScanSummary(durationMs = duration))
                            val summaryText = buildSummary(result)
                            val ok = _hosts.isNotEmpty() || _urls.isNotEmpty()
                            _state.update { it.copy(isScanning = false, isPaused = false, scanType = null,
                                progress = "", progressPercent = 1f, summary = summaryText,
                                summaryColor = if (ok) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = ok,
                                hostSummary = buildHostSummary(result), scanResult = result) }
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isScanning = false, summary = "Cancelled", summaryColor = 0xFFC62828) }
            }
        }
    }

    fun pauseScan() {
        scannerManager.pause()
        _state.update { it.copy(isPaused = true, summary = "Paused — ${_hosts.size} host(s) ditemukan",
            summaryColor = 0xFFE65100, isSummaryOk = true) }
    }

    fun resumeScan() {
        scannerManager.resume()
        _state.update { it.copy(isPaused = false, summary = "Resuming...", summaryColor = 0xFF00695C, isSummaryOk = true) }
    }

    fun useCustomPortsForScan() {
        val profile = _state.value.selectedProfile
        val custom = _state.value.customPorts
        com.tasirin.network.radar.scanner.PortScanner.customPortsOverride = when {
            profile != PortProfile.DEFAULT -> profile.ports
            custom.isNotBlank() -> PortRangeParser.parse(custom)
            else -> null
        }
    }

    fun stopScan() {
        scannerManager.stop()
        monitorJob?.cancel(); monitorJob = null
        persistResults(force = true)
        _state.update { it.copy(isScanning = false, isPaused = false, summary = "Stopped",
            summaryColor = 0xFFC62828, isSummaryOk = false, monitor = PingMonitorState()) }
    }

    private fun startMonitor(target: String) {
        val ip = NetworkUtils.resolveDomain(target) ?: target
        _state.update { it.copy(isScanning = true, scanType = ScanType.MONITOR, error = null,
            summary = "Monitoring $ip...", summaryColor = 0xFF00695C, isSummaryOk = true,
            monitor = PingMonitorState(ip = ip, isRunning = true, history = emptyList())) }
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                val latency = PingUtil.ping(ip)
                val result = PingResult(latency, latency != null)
                _state.update {
                    val history = (it.monitor.history + result).takeLast(50)
                    it.copy(monitor = it.monitor.copy(lastLatency = latency, history = history),
                        summary = if (latency != null) "ping ${ip} — ${latency}ms" else "ping ${ip} — ✗",
                        summaryColor = if (latency != null) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = latency != null)
                }
                delay(1500)
            }
        }
    }

    fun setSortMode(mode: SortMode) { _state.update { it.copy(sortMode = mode) }; applySort() }

    private fun applySort() {
        val mode = _state.value.sortMode
        val sorted = when (mode) {
            SortMode.IP -> _hosts.values.sortedBy { it.ip }
            SortMode.PORTS -> _hosts.values.sortedByDescending { it.openPorts.size }
            SortMode.LATENCY -> _hosts.values.sortedBy { it.latencyMs ?: Long.MAX_VALUE }
            SortMode.HOSTNAME -> _hosts.values.sortedBy { it.hostname ?: it.ip }
        }
        _state.update { it.copy(hosts = sorted) }
    }

    fun copyToClipboard(label: String, text: String) {
        try {
            val ctx = getApplication<Application>()
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            _state.update { it.copy(copyFeedback = "Copied: $text") }
        } catch (_: Exception) { _state.update { it.copy(error = "Failed to copy") } }
    }

    fun copyAllText(): String {
        val sb = StringBuilder()
        sb.appendLine("NetRadar Scan Results")
        sb.appendLine("Target: ${_state.value.target.ifBlank { "-" }}")
        sb.appendLine("Hosts: ${_hosts.size}  URLs: ${_urls.size}")
        sb.appendLine("---")
        for (host in _hosts.values) {
            sb.append(host.ip)
            host.hostname?.let { if (it != host.ip) sb.append(" ($it)") }
            host.macAddress?.let { sb.append(" $it") }
            host.macVendor?.let { sb.append(" ($it)") }
            sb.appendLine()
            for (p in host.openPorts) sb.appendLine("  ${host.ip}:${p.port}  ${p.service ?: ""}")
        }
        for (url in _urls.values) sb.appendLine("  ${url.url}  [${url.statusCode}] ${url.title ?: ""}")
        sb.appendLine("---")
        val hosts = _hosts.values.toList()
        val urls = _urls.values.toList()
        sb.appendLine(buildSummary(ScanResult(_state.value.scanType ?: ScanType.PORT_SCAN, _state.value.target, hosts = hosts, discoveredUrls = urls)))
        return sb.toString()
    }

    fun deleteHost(ip: String) {
        _hosts.remove(ip)
        _state.update {
            it.copy(hosts = _hosts.values.toList(), summary = "Removed $ip — ${_hosts.size} host(s) tersisa",
                summaryColor = 0xFFC62828, isSummaryOk = false)
        }
        persistResults(force = true)
    }

    fun clearResults() {
        _hosts.clear(); _urls.clear()
        _state.update {
            it.copy(hosts = emptyList(), discoveredUrls = emptyList(), hostSummary = "", scanResult = null,
                summary = "Results cleared", summaryColor = 0xFF00695C, isSummaryOk = true)
        }
        persistResults(force = true)
    }

    fun wakeOnLan(ip: String, mac: String) {
        viewModelScope.launch {
            val success = WakeOnLan.wake(ip, mac)
            _state.update { it.copy(summary = if (success) "WoL sent to $mac" else "WoL failed",
                summaryColor = if (success) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = success) }
        }
    }


    fun toggleDarkTheme() {
        _state.update { it.copy(isDarkTheme = when (it.isDarkTheme) { null -> true; true -> false; false -> null }) }
    }

    private fun buildSummary(result: ScanResult): String {
        val parts = mutableListOf<String>()
        if (result.hosts.isNotEmpty()) {
            parts.add("${result.hosts.size} host(s)")
            val totalPorts = result.hosts.sumOf { it.openPorts.size }
            if (totalPorts > 0) parts.add("$totalPorts port(s)")
        }
        if (result.discoveredUrls.isNotEmpty()) parts.add("${result.discoveredUrls.size} URL(s)")
        if (result.summary.durationMs > 0) parts.add("${result.summary.durationMs / 1000}s")
        return if (parts.isEmpty()) "No results found" else parts.joinToString(" · ")
    }

    private fun buildHostSummary(result: ScanResult): String {
        val cameras = result.hosts.count { h -> h.openPorts.any { p ->
            p.service?.contains("Camera", true) == true || p.service?.contains("Hikvision", true) == true ||
            p.service?.contains("Dahua", true) == true || p.service?.contains("ONVIF", true) == true ||
            p.service?.contains("RTSP", true) == true } }
        val routers = result.hosts.count { h -> h.openPorts.any { p ->
            p.service?.contains("Router", true) == true || p.service?.contains("MikroTik", true) == true ||
            p.service?.contains("Winbox", true) == true || p.service?.contains("TR-069", true) == true ||
            p.service?.contains("UPnP", true) == true } }
        val shares = result.hosts.count { h -> h.openPorts.any { p -> p.port in listOf(445, 139, 2049, 21, 111, 135) } }
        val parts = mutableListOf<String>()
        if (cameras > 0) parts.add("📷 $cameras")
        if (routers > 0) parts.add("🌐 $routers")
        if (shares > 0) parts.add("📁 $shares")
        return parts.joinToString("  ")
    }

    private fun persistResults(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 2_000) return  // throttle: max tiap 2 detik saat scan
        lastPersistAt = now
        val hosts = _hosts.values.toList()
        val urls = _urls.values.toList()
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { ResultsStore.save(app, hosts, urls) }
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stop()
        monitorJob?.cancel()
        ResultsStore.save(getApplication(), _hosts.values.toList(), _urls.values.toList())
    }
}
