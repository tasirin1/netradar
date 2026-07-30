package com.example.networkscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.networkscanner.db.ScanHistoryStore
import com.example.networkscanner.export.Exporter
import com.example.networkscanner.model.*
import com.example.networkscanner.scanner.ScannerManager
import com.example.networkscanner.util.NetworkUtils
import com.example.networkscanner.util.WakeOnLan
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ScanUiState(
    val target: String = "",
    val isScanning: Boolean = false,
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
    val history: List<com.example.networkscanner.db.HistoryEntry> = emptyList(),
    val isDarkTheme: Boolean? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = ScannerManager()
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _hosts = mutableListOf<HostInfo>()
    private val _urls = mutableListOf<UrlDiscovery>()
    private var _startTime = 0L

    init { loadHistory() }

    fun setTarget(target: String) { _state.update { it.copy(target = target) } }

    fun startScan(type: ScanType) {
        val target = _state.value.target.trim()
        if (target.isEmpty()) {
            _state.update { it.copy(error = "Enter target IP or URL") }
            return
        }

        _hosts.clear()
        _urls.clear()
        _startTime = System.currentTimeMillis()

        _state.update {
            it.copy(
                isScanning = true, scanType = type, error = null,
                hosts = emptyList(), discoveredUrls = emptyList(),
                summary = "${type.label} starting...", summaryColor = 0xFF00695C,
                isSummaryOk = true, progress = "", progressPercent = 0f,
                hostSummary = "", scanResult = null
            )
        }

        viewModelScope.launch {
            scannerManager.scan(type, target).collect { event ->
                when (event) {
                    is ScanEvent.Progress -> {
                        val pct = if (event.total > 0) event.current.toFloat() / event.total else 0f
                        _state.update { it.copy(progress = "Scanning ${event.ip}...", progressPercent = pct) }
                    }
                    is ScanEvent.HostFound -> {
                        _hosts.add(event.host)
                        _state.update { it.copy(hosts = _hosts.toList()) }
                    }
                    is ScanEvent.UrlFound -> {
                        _urls.add(event.url)
                        _state.update { it.copy(discoveredUrls = _urls.toList()) }
                    }
                    is ScanEvent.Error -> _state.update { it.copy(error = event.message) }
                    is ScanEvent.Complete -> {
                        val duration = System.currentTimeMillis() - _startTime
                        val result = event.result.copy(
                            hosts = _hosts.toList(),
                            discoveredUrls = _urls.toList(),
                            summary = ScanSummary(
                                totalHosts = _hosts.size,
                                aliveHosts = _hosts.count { it.isAlive },
                                openPorts = _hosts.sumOf { it.openPorts.size },
                                urlsFound = _urls.size, durationMs = duration
                            )
                        )
                        val summaryText = buildSummary(result)
                        val summaryColor = if (_hosts.isNotEmpty() || _urls.isNotEmpty()) 0xFF2E7D32 else 0xFFC62828
                        val ok = _hosts.isNotEmpty() || _urls.isNotEmpty()
                        val app = getApplication<Application>()
                        ScanHistoryStore.addEntry(app, result, summaryText)
                        _state.update {
                            it.copy(
                                isScanning = false, scanType = null, progress = "", progressPercent = 1f,
                                summary = summaryText, summaryColor = summaryColor, isSummaryOk = ok,
                                hostSummary = buildHostSummary(result), scanResult = result,
                                history = ScanHistoryStore.load(app)
                            )
                        }
                    }
                }
            }
        }
    }

    fun stopScan() {
        scannerManager.stop()
        _state.update { it.copy(isScanning = false, summary = "Stopped", summaryColor = 0xFFC62828, isSummaryOk = false) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    fun exportJson() { shareExport(Exporter::exportToJson) }
    fun exportCsv() { shareExport(Exporter::exportToCsv) }

    fun exportTxt() {
        val result = _state.value.scanResult ?: return
        val app = getApplication<Application>()
        val file = File(app.cacheDir, "scan_${result.timestamp}.txt")
        file.writeText(Exporter.exportToTxt(app, result))
        Exporter.shareFile(app, file)
    }

    private fun shareExport(fn: (android.content.Context, ScanResult) -> java.io.File) {
        val result = _state.value.scanResult ?: return
        val app = getApplication<Application>()
        val file = fn(app, result)
        Exporter.shareFile(app, file)
    }

    fun loadHistory() {
        val app = getApplication<Application>()
        _state.update { it.copy(history = ScanHistoryStore.load(app)) }
    }

    fun loadHistoryEntry(id: Long) {
        val app = getApplication<Application>()
        val result = ScanHistoryStore.getEntry(app, id) ?: return
        _state.update {
            it.copy(hosts = result.hosts, discoveredUrls = result.discoveredUrls,
                hostSummary = buildHostSummary(result), scanResult = result,
                summary = "Loaded from history", summaryColor = 0xFF00695C, isSummaryOk = true)
        }
    }

    fun clearHistory() {
        val app = getApplication<Application>()
        ScanHistoryStore.clear(app)
        _state.update { it.copy(history = emptyList()) }
    }

    fun wakeOnLan(ip: String, mac: String) {
        viewModelScope.launch {
            val success = WakeOnLan.wake(ip, mac)
            _state.update {
                it.copy(summary = if (success) "WoL packet sent to $mac" else "WoL failed",
                    summaryColor = if (success) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = success)
            }
        }
    }

    fun getLocalNetworkHint(): String = NetworkUtils.getLocalNetworkPrefix()?.let { "$it.0/24" } ?: ""

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

    override fun onCleared() { super.onCleared(); scannerManager.stop() }
}
