package com.tasirin.network.radar.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    val scanSpeed: ScanSpeed = ScanSpeed.SEDANG,
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val scanType: ScanType? = null,
    val progress: String = "",
    val progressPercent: Float = 0f,
    val hosts: List<HostInfo> = emptyList(),
    val discoveredUrls: List<UrlDiscovery> = emptyList(),
    val selectedHosts: Set<String> = emptySet(),
    val searchQuery: String = "",
    val deviceFilter: DeviceFilter = DeviceFilter.ALL,
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
    private val portScanner = com.tasirin.network.radar.scanner.PortScanner()
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _hosts = linkedMapOf<String, HostInfo>()
    private val _urls = linkedMapOf<String, UrlDiscovery>()
    private var _startTime = 0L
    private var monitorJob: Job? = null
    private var lastPersistAt = 0L
    private var _lastDeleted: List<HostInfo> = emptyList()
    private var lastNotifyAt = 0L
    private var notifyCount = 0

    private companion object {
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val MAX_NOTIFY_PER_SCAN = 20
    }

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
    fun setScanSpeed(speed: ScanSpeed) { _state.update { it.copy(scanSpeed = speed) } }
    fun setSearchQuery(query: String) { _state.update { it.copy(searchQuery = query) } }
    fun setDeviceFilter(filter: DeviceFilter) { _state.update { it.copy(deviceFilter = filter) } }
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
        lastNotifyAt = 0L; notifyCount = 0; _lastDeleted = emptyList()
        _state.update {
            it.copy(isScanning = true, isPaused = false, scanType = type, error = null,
                summary = "${type.label} starting...",
                summaryColor = 0xFF00695C, isSummaryOk = true, progress = "", progressPercent = 0f,
                hostSummary = "", scanResult = null, selectedHosts = emptySet())
        }

        viewModelScope.launch {
            try {
                scannerManager.scan(type, target, speed = _state.value.scanSpeed).collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> {
                            val pct = if (event.total > 0) event.current.toFloat() / event.total else 0f
                            val pctText = if (event.total > 0) " (${event.current}/${event.total})" else ""
                            _state.update { it.copy(progress = "${event.ip}$pctText", progressPercent = pct) }
                        }
                        is ScanEvent.HostFound -> {
                            val isNew = !_hosts.containsKey(event.host.ip)
                            val host = if (isNew) event.host.copy(isNew = true) else event.host
                            _hosts[host.ip] = host
                            if (_hosts.size <= 500) applySort()
                            else _state.update { it.copy(hosts = _hosts.values.toList()) }
                            if (isNew && type != ScanType.TRACE) notifyNewDevice(host)
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

    fun stopScan() {
        scannerManager.stop()
        monitorJob?.cancel(); monitorJob = null
        persistResults(force = true)
        _state.update { it.copy(isScanning = false, isPaused = false, summary = "Stopped",
            summaryColor = 0xFFC62828, isSummaryOk = false, monitor = PingMonitorState(), selectedHosts = emptySet()) }
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

    fun toggleHostSelection(ip: String) {
        _state.update {
            val sel = it.selectedHosts.toMutableSet()
            if (!sel.add(ip)) sel.remove(ip)
            it.copy(selectedHosts = sel)
        }
    }

    fun selectAllHosts() {
        _state.update { it.copy(selectedHosts = _hosts.keys.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedHosts = emptySet()) }
    }

    fun deleteSelectedHosts() {
        val toDelete = _state.value.selectedHosts
        if (toDelete.isEmpty()) return
        _lastDeleted = toDelete.mapNotNull { _hosts[it] }
        toDelete.forEach { _hosts.remove(it) }
        _state.update {
            it.copy(hosts = _hosts.values.toList(), selectedHosts = emptySet(),
                summary = "Removed ${toDelete.size} host(s) — ${_hosts.size} tersisa",
                summaryColor = 0xFFC62828, isSummaryOk = false)
        }
        persistResults(force = true)
    }

    fun undoDelete() {
        if (_lastDeleted.isEmpty()) return
        val restored = _lastDeleted.filter { !_hosts.containsKey(it.ip) }
        _lastDeleted = emptyList()
        if (restored.isEmpty()) return
        restored.forEach { _hosts[it.ip] = it }
        _state.update {
            it.copy(hosts = _hosts.values.toList(), summary = "Undo: ${restored.size} host(s) dikembalikan",
                summaryColor = 0xFF00695C, isSummaryOk = true)
        }
        persistResults(force = true)
    }

    fun rescanHost(ip: String) {
        if (_state.value.isScanning) return
        _state.update { it.copy(summary = "Scan ulang $ip...", summaryColor = 0xFF00695C, isSummaryOk = true) }
        viewModelScope.launch {
            try {
                val host = withContext(Dispatchers.IO) { portScanner.scanHost(ip, speed = _state.value.scanSpeed) }
                val ports = host?.openPorts?.size ?: 0
                if (host != null) _hosts[ip] = host
                _state.update {
                    it.copy(hosts = _hosts.values.toList(),
                        summary = if (ports > 0) "Rescan $ip: $ports port terbuka"
                        else "Rescan $ip: tidak ada port terbuka",
                        summaryColor = 0xFF2E7D32, isSummaryOk = true)
                }
                persistResults(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Rescan $ip gagal") }
            }
        }
    }

    fun clearResults() {
        _hosts.clear(); _urls.clear(); _lastDeleted = emptyList()
        _state.update {
            it.copy(hosts = emptyList(), discoveredUrls = emptyList(), hostSummary = "", scanResult = null,
                selectedHosts = emptySet(), searchQuery = "", deviceFilter = DeviceFilter.ALL,
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
        val cameras = result.hosts.count { DeviceKind.CAMERA in it.deviceKinds() }
        val routers = result.hosts.count { DeviceKind.ROUTER in it.deviceKinds() }
        val shares = result.hosts.count { DeviceKind.SHARE in it.deviceKinds() }
        val parts = mutableListOf<String>()
        if (cameras > 0) parts.add("📷 $cameras")
        if (routers > 0) parts.add("🌐 $routers")
        if (shares > 0) parts.add("📁 $shares")
        return parts.joinToString("  ")
    }

    /** Notifikasi perangkat baru, di-throttle (maks 20/scan, min 2 detik antar notif). */
    private fun notifyNewDevice(host: HostInfo) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 2_000 || notifyCount >= MAX_NOTIFY_PER_SCAN) return
        lastNotifyAt = now; notifyCount++
        try {
            val ctx = getApplication<Application>()
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_NEW_DEVICE, "Perangkat Baru", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val detail = buildString {
                host.hostname?.takeIf { it != host.ip }?.let { append(it) }
                host.macVendor?.let { if (isNotEmpty()) append(" · "); append(it) }
            }
            val notification = NotificationCompat.Builder(ctx, CHANNEL_NEW_DEVICE)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("Perangkat baru: ${host.ip}")
                .setContentText(detail.ifBlank { "Host baru terdeteksi di jaringan" })
                .setAutoCancel(true)
                .build()
            nm.notify(host.ip.hashCode(), notification)
        } catch (_: Exception) { }
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
