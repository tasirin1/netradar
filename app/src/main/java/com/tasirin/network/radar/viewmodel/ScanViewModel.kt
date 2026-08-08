package com.tasirin.network.radar.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ScanService
import com.tasirin.network.radar.scanner.ScanCheckpoint
import com.tasirin.network.radar.scanner.ScanLoop
import com.tasirin.network.radar.scanner.ScannerManager
import com.tasirin.network.radar.util.FavoritesStore
import com.tasirin.network.radar.util.AppForeground
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import com.tasirin.network.radar.util.PingStore
import com.tasirin.network.radar.util.ResultsStore
import com.tasirin.network.radar.util.ScanCheckpointStore
import com.tasirin.network.radar.util.ScanHistoryStore
import com.tasirin.network.radar.util.AppSettings
import com.tasirin.network.radar.util.SettingsStore
import com.tasirin.network.radar.util.SoundFeedback
import com.tasirin.network.radar.util.UptimeStore
import com.tasirin.network.radar.util.WakeOnLan
import com.tasirin.network.radar.widget.NetRadarWidget
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
    val showSettings: Boolean = false,
    val networkInfo: NetworkInfo = NetworkInfo(),
    val sortMode: SortMode = SortMode.IP,
    val monitor: MonitorState = MonitorState(),
    val copyFeedback: String? = null,
    val favoriteIps: Set<String> = emptySet(),
    val uptime: Map<String, List<UptimeEvent>> = emptyMap(),
    val pingHistory: Map<String, List<PingEvent>> = emptyMap(),
    val statusFilter: HostStatusFilter = HostStatusFilter.ALL,
    val diff: ScanDiff? = null,
    val deepScanning: String? = null,
    val deepScanProgress: Int = 0,
    val scanHistory: List<ScanHistoryEntry> = emptyList(),
    val gatewayOnline: Boolean? = null,
    val gatewayLatencyMs: Long? = null,
    val internetOnline: Boolean? = null,
    val internetLatencyMs: Long? = null,
    val networkQualityLabel: String = "",
    val networkQualityColor: Long = 0xFF00695C,
    val notifyNewDevices: Boolean = true,
    val notifyImportantOffline: Boolean = true,
    val notifyScanDone: Boolean = true,
    val keepScreenOn: Boolean = true,
    val soundEnabled: Boolean = true,
    val autoDiffDialog: Boolean = true,
    val compactMode: Boolean = false,
    val openDiffDialog: Boolean = false,
    val pendingWideTarget: String? = null,
    val pendingWideScanType: ScanType? = null,
    val pendingWideCount: Long = 0L,
    val canResumeScan: Boolean = false,
    val resumeInfo: String = "",
    val recentTargets: List<String> = emptyList(),
    val monitorFavoritesOnly: Boolean = false,
    val staleIps: Set<String> = emptySet()
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
    private var _deepScanJob: Job? = null
    private var lastPersistAt = 0L
    private var _lastDeleted: List<HostInfo> = emptyList()
    private var lastNotifyAt = 0L
    private var notifyCount = 0
    private var scanGeneration = 0L
    private var lastScanNotifAt = 0L
    private var lastFavoriteAlertAt = 0L
    private var _favorites = mutableSetOf<String>()
    private var _uptime = emptyMap<String, List<UptimeEvent>>()
    private var _pingHistory = emptyMap<String, List<PingEvent>>()
    private var _history = emptyList<ScanHistoryEntry>()
    private var _foundThisScan = linkedMapOf<String, List<Int>>()
    private var _previousScanHosts: Map<String, List<Int>>? = null
    private var lastHostUiAt = 0L
    private var gatewayJob: Job? = null
    private var lastBackOnlineAt = 0L
    private val gatewayLatencies = ArrayDeque<Long>()
    private var lastMonitorSaveAt = 0L
    private var lastWidgetAt = 0L
    private var _checkpoint: ScanCheckpointStore.Checkpoint? = null
    private var lastCheckpointSaveAt = 0L
    private var _scanCount = 0L

    private companion object {
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val CHANNEL_IMPORTANT = "important_device"
        const val MAX_NOTIFY_PER_SCAN = 20
        const val WIDE_SCAN_THRESHOLD = 256  // > 256 subnet (≈65 ribu IP) → minta konfirmasi
    }

    init {
        refreshNetworkInfo()
        // Muat hasil dari sesi sebelumnya agar tidak hilang saat app ditutup
        val (hosts, urls) = ResultsStore.load(getApplication())
        hosts.forEach { _hosts[it.ip] = it }
        urls.forEach { _urls[it.url] = it }
        _favorites = FavoritesStore.load(getApplication()).toMutableSet()
        _uptime = UptimeStore.load(getApplication())
        _pingHistory = PingStore.load(getApplication())
        _history = ScanHistoryStore.load(getApplication())
        _scanCount = ResultsStore.loadScanCount(getApplication())
        _checkpoint = ScanCheckpointStore.load(getApplication())
        val settings = SettingsStore.load(getApplication())
        val recent = buildRecentTargets(_history, _checkpoint)
        val cp = _checkpoint
        _state.update {
            it.copy(
                hosts = _hosts.values.toList(),
                discoveredUrls = _urls.values.toList(),
                favoriteIps = _favorites.toSet(),
                uptime = _uptime,
                pingHistory = _pingHistory,
                scanHistory = _history,
                isDarkTheme = settings.darkTheme,
                notifyNewDevices = settings.notifyNewDevices,
                notifyImportantOffline = settings.notifyImportantOffline,
                notifyScanDone = settings.notifyScanDone,
                keepScreenOn = settings.keepScreenOn,
                soundEnabled = settings.soundEnabled,
                autoDiffDialog = settings.autoDiffDialog,
                compactMode = settings.compactMode,
                monitorFavoritesOnly = settings.monitorFavoritesOnly,
                recentTargets = recent,
                canResumeScan = cp != null,
                resumeInfo = cp?.let { resumeInfoText(it) } ?: "",
                summary = if (_hosts.isEmpty()) "Ready" else "Riwayat: ${_hosts.size} host(s), ${_urls.size} URL(s)"
            )
        }
        updateStaleIps()
        startGatewayMonitor()
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
    fun setStatusFilter(filter: HostStatusFilter) { _state.update { it.copy(statusFilter = filter) } }
    fun toggleAbout() { _state.update { it.copy(showAbout = !it.showAbout) } }

    fun startScan(type: ScanType, force: Boolean = false) {
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

        // Scan area luas (banyak subnet) butuh konfirmasi dulu + estimasi
        if (!force) {
            val subnets = NetworkUtils.expandTargetSubnets(target)
            if (subnets.size > WIDE_SCAN_THRESHOLD) {
                _state.update {
                    it.copy(pendingWideTarget = target, pendingWideScanType = type,
                        pendingWideCount = subnets.sumOf { (it.hostEnd - it.hostStart + 1).toLong() })
                }
                return
            }
        }

        _startTime = System.currentTimeMillis()
        lastNotifyAt = 0L; notifyCount = 0; _lastDeleted = emptyList()
        lastScanNotifAt = 0L
        _foundThisScan = linkedMapOf()
        _state.update {
            it.copy(isScanning = true, isPaused = false, scanType = type, error = null,
                summary = "${type.label} starting...",
                summaryColor = 0xFF00695C, isSummaryOk = true, progress = "", progressPercent = 0f,
                hostSummary = "", scanResult = null, selectedHosts = emptySet(), diff = null)
        }
        val gen = ++scanGeneration
        startScanService()

        viewModelScope.launch {
            try {
                scannerManager.scan(type, target, speed = _state.value.scanSpeed).collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> {
                            val pct = if (event.total > 0) event.current.toFloat() / event.total else 0f
                            val pctText = if (event.total > 0) " (${event.current}/${event.total})" else ""
                            val eta = estimateEta(event.current, event.total)
                            _state.update { it.copy(progress = "${event.ip}$pctText$eta", progressPercent = pct) }
                            // Simpan posisi terakhir (subnet + host) untuk fitur lanjutkan scan
                            if (event.subnetIndex >= 0) saveCheckpoint(type, target, event)
                            updateScanNotification(event)
                        }
                        is ScanEvent.HostFound -> {
                            _foundThisScan[event.host.ip] = event.host.openPorts.map { it.port }
                            val existing = _hosts[event.host.ip]
                            val isNew = existing == null
                            val host = if (existing != null) {
                                mergeHost(existing, event.host).copy(isNew = false)
                            } else event.host.copy(isNew = true)
                            // Gabungkan data lintas scan + tandai scan terakhir terlihat
                            val merged = host.copy(lastSeenScan = _scanCount)
                            _hosts[merged.ip] = merged
                            refreshHostsUi()
                            if (isNew && type != ScanType.TRACE) notifyNewDevice(merged)
                            if (_state.value.soundEnabled) {
                                SoundFeedback.playForPort(event.host.openPorts.firstOrNull()?.port ?: 0)
                            }
                            if (merged.ip in _favorites && !isNew &&
                                _uptime[merged.ip]?.lastOrNull()?.online == false) {
                                notifyFavoriteBackOnline(merged.ip)
                            }
                            recordUptime(event.host.ip, true)
                            event.host.latencyMs?.let { recordPing(merged.ip, it) }
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
                            computeDiff()
                            refreshHostsUi(force = true)
                            persistResults(force = true)
                            val result = event.result.copy(hosts = _hosts.values.toList(),
                                discoveredUrls = _urls.values.toList(),
                                summary = ScanSummary(durationMs = duration))
                            val summaryText = buildSummary(result)
                            val diffText = _state.value.diff?.let { d ->
                                if (d.added.isNotEmpty() || d.removed.isNotEmpty() || d.changed.isNotEmpty())
                                    " · +${d.added.size} baru -${d.removed.size} hilang ~${d.changed.size} berubah" else null
                            }
                            val finalSummary = if (diffText != null) "$summaryText$diffText" else summaryText
                            val ok = _hosts.isNotEmpty() || _urls.isNotEmpty()
                            recordHistory(type.label, target, _hosts.size,
                                _hosts.values.sumOf { it.openPorts.size }, duration)
                            postScanDoneNotification("NetRadar — scan selesai", finalSummary)
                            val diff = _state.value.diff
                            val openDiff = _state.value.autoDiffDialog && diff != null &&
                                (diff.added.isNotEmpty() || diff.removed.isNotEmpty() || diff.changed.isNotEmpty())
                            _state.update { it.copy(isScanning = false, isPaused = false, scanType = null,
                                progress = "", progressPercent = 1f, summary = finalSummary,
                                summaryColor = if (ok) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = ok,
                                hostSummary = buildHostSummary(result), scanResult = result,
                                openDiffDialog = openDiff) }
                            if (gen == scanGeneration) stopScanService()
                            // Scan tuntas → posisi resume tidak diperlukan lagi
                            clearCheckpoint(keepState = false)
                            _scanCount++
                            ResultsStore.saveScanCount(getApplication(), _scanCount)
                            updateStaleIps()
                            refreshRecentTargets()
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                if (gen == scanGeneration) stopScanService()
                refreshHostsUi(force = true)
                _state.update { it.copy(isScanning = false, summary = "Cancelled", summaryColor = 0xFFC62828) }
            }
        }
    }

    /** Lanjutkan scan area luas setelah konfirmasi pengguna. */
    fun confirmWideScan() {
        val st = _state.value
        val type = st.pendingWideScanType ?: return
        _state.update { it.copy(pendingWideTarget = null, pendingWideScanType = null, pendingWideCount = 0L) }
        startScan(type, force = true)
    }

    /** Batalkan scan area luas. */
    fun cancelWideScan() {
        _state.update { it.copy(pendingWideTarget = null, pendingWideScanType = null, pendingWideCount = 0L) }
    }

    fun pauseScan() {
        scannerManager.pause()
        refreshHostsUi(force = true)
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
        _deepScanJob?.cancel(); _deepScanJob = null
        stopScanService()
        persistCheckpoint(force = true)  // simpan posisi agar bisa dilanjutkan nanti
        persistResults(force = true)
        refreshHostsUi(force = true)
        _state.update { it.copy(isScanning = false, isPaused = false, deepScanning = null, deepScanProgress = 0,
            summary = "Stopped",
            summaryColor = 0xFFC62828, isSummaryOk = false, monitor = MonitorState(), selectedHosts = emptySet()) }
    }

    /** Lanjutkan scan terakhir dari posisi yang tersimpan (banner "Lanjutkan"). */
    fun resumeLastScan() {
        val cp = _checkpoint ?: return
        val type = ScanType.entries.firstOrNull { it.label == cp.type } ?: return
        ScanCheckpoint.setResume(cp.subnetIndex, cp.hostOffset)
        _state.update { it.copy(target = cp.target) }
        startScan(type, force = true)
    }

    /** Hapus posisi scan tersimpan (tombol "Mulai baru" / X di banner). */
    fun clearCheckpoint() {
        clearCheckpoint(keepState = false)
    }

    private fun clearCheckpoint(keepState: Boolean) {
        ScanCheckpoint.reset()
        _checkpoint = null
        ScanCheckpointStore.save(getApplication(), null)
        if (!keepState) {
            _state.update { it.copy(canResumeScan = false, resumeInfo = "") }
        }
    }

    private fun saveCheckpoint(type: ScanType, target: String, event: ScanEvent.Progress) {
        // Traceroute & URL Path tidak pakai scanSubnets → tidak bisa di-resume
        if (type == ScanType.TRACE || type == ScanType.URL_PATH) return
        val cp = ScanCheckpointStore.Checkpoint(
            target = target,
            type = type.label,
            subnetIndex = event.subnetIndex,
            hostOffset = event.hostOffset,
            total = event.total.toLong(),
            lastIp = event.ip,
            updatedAt = System.currentTimeMillis()
        )
        _checkpoint = cp
        persistCheckpoint()
    }

    private fun persistCheckpoint(force: Boolean = false) {
        val cp = _checkpoint ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckpointSaveAt < 3_000) return
        lastCheckpointSaveAt = now
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { ScanCheckpointStore.save(app, cp) }
    }

    /** Gabungkan data host lama + baru lintas scan agar tidak ada data yang hilang. */
    private fun mergeHost(existing: HostInfo, fresh: HostInfo): HostInfo {
        val mac = fresh.macAddress ?: existing.macAddress
        val mergedPorts = (fresh.openPorts + existing.openPorts).distinctBy { it.port }
        return HostInfo(
            ip = fresh.ip,
            hostname = fresh.hostname ?: existing.hostname,
            label = existing.label ?: fresh.label,
            macAddress = mac,
            macVendor = fresh.macVendor ?: existing.macVendor ?: mac?.let { NetworkUtils.lookupMacVendor(it) },
            latencyMs = fresh.latencyMs ?: existing.latencyMs,
            osGuess = fresh.osGuess ?: existing.osGuess,
            isAlive = fresh.isAlive || existing.isAlive,
            openPorts = mergedPorts,
            isNew = false,
            ipConflict = existing.ipConflict || (fresh.macAddress != null && existing.macAddress != null &&
                !fresh.macAddress.equals(existing.macAddress, ignoreCase = true)),
            lastSeenScan = fresh.lastSeenScan
        )
    }

    private fun resumeInfoText(cp: ScanCheckpointStore.Checkpoint): String {
        val type = ScanType.entries.firstOrNull { it.label == cp.type }?.label ?: cp.type
        val remaining = (cp.total - (cp.subnetIndex.toLong() * 254 + cp.hostOffset)).coerceAtLeast(0)
        val last = cp.lastIp.ifBlank { cp.target }
        return "$type · berhenti di $last · sisa ±$remaining IP"
    }

    private fun buildRecentTargets(
        history: List<ScanHistoryEntry>,
        cp: ScanCheckpointStore.Checkpoint?
    ): List<String> {
        val list = mutableListOf<String>()
        cp?.target?.takeIf { it.isNotBlank() }?.let { if (it !in list) list.add(it) }
        history.forEach { h ->
            val t = h.target.trim()
            if (t.isNotEmpty() && t !in list) list.add(t)
        }
        return list.take(8)
    }

    private fun refreshRecentTargets() {
        _state.update { it.copy(recentTargets = buildRecentTargets(_history, _checkpoint)) }
    }

    /** Tandai host yang tidak muncul dalam 2 scan terakhir sebagai "Lama". */
    private fun updateStaleIps() {
        val stale = _hosts.values
            .filter { it.lastSeenScan > 0 && _scanCount - it.lastSeenScan >= 2 }
            .map { it.ip }
            .toSet()
        _state.update { it.copy(staleIps = stale) }
    }

    fun setMonitorFavoritesOnly(enabled: Boolean) {
        _state.update { it.copy(monitorFavoritesOnly = enabled) }
        persistSettings()
    }

    private fun startMonitor(target: String) {
        val favoritesOnly = _state.value.monitorFavoritesOnly
        val ips = if (favoritesOnly) _favorites.toList().sorted()
        else _hosts.values.map { it.ip }.sorted()
        if (ips.isEmpty()) {
            if (favoritesOnly) {
                _state.update { it.copy(error = "Belum ada favorit — bintangi perangkat dulu (⭐)") }
                return
            }
            startSingleMonitor(target)
            return
        }
        _state.update { it.copy(isScanning = true, scanType = ScanType.MONITOR, error = null,
            summary = "Monitoring ${ips.size} perangkat...", summaryColor = 0xFF00695C, isSummaryOk = true,
            monitor = MonitorState(isRunning = true)) }
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            var pings = 0
            while (isActive) {
                // Sinkronkan daftar host tiap siklus: host baru ikut, host dihapus keluar
                val currentIps = if (favoritesOnly) _favorites.toList().sorted()
                else _hosts.values.map { it.ip }.sorted()
                if (currentIps.isEmpty()) break
                // pingProbe memblokir (ProcessBuilder), jadi jalankan di IO
                val probes = withContext(Dispatchers.IO) {
                    currentIps.chunked(8).flatMap { chunk ->
                        chunk.map { ip -> async { ip to PingUtil.pingProbe(ip) } }.map { it.await() }
                    }
                }
                val statuses = probes.associate { (ip, probe) -> ip to (probe != null) }
                val latUpdates = probes.mapNotNull { (ip, probe) ->
                    if (probe != null) ip to probe.latencyMs else null
                }.toMap()
                val now = System.currentTimeMillis()
                val persist = now - lastMonitorSaveAt >= 10_000
                if (persist) lastMonitorSaveAt = now
                // Batch uptime + ping: satu save untuk semua host per siklus
                _uptime = UptimeStore.recordBatch(getApplication(), _uptime, statuses, persist)
                _pingHistory = PingStore.recordBatch(getApplication(), _pingHistory, latUpdates, persist)
                pings++
                val prevStatuses = _state.value.monitor.statuses
                statuses.forEach { (ip, online) ->
                    if (online && ip in _favorites && prevStatuses[ip] == false) {
                        notifyFavoriteBackOnline(ip)
                    } else if (!online && ip in _favorites &&
                        System.currentTimeMillis() - lastFavoriteAlertAt > 30_000) {
                        lastFavoriteAlertAt = System.currentTimeMillis()
                        notifyImportantOffline(ip)
                    }
                }
                val onlineCount = statuses.values.count { it }
                _state.update {
                    it.copy(monitor = MonitorState(isRunning = true, statuses = statuses, pings = pings),
                        uptime = _uptime,
                        pingHistory = _pingHistory,
                        summary = "Monitor: $onlineCount/${statuses.size} online",
                        summaryColor = if (onlineCount > 0) 0xFF2E7D32 else 0xFFC62828,
                        isSummaryOk = onlineCount > 0)
                }
                delay(3000)
            }
            // Semua host terhapus saat monitor berjalan → berhenti rapi
            if (isActive) {
                _state.update {
                    it.copy(monitor = MonitorState(), isScanning = false, scanType = null,
                        summary = "Monitor berhenti — semua host dihapus",
                        summaryColor = 0xFFC62828, isSummaryOk = false)
                }
            }
        }
    }

    private fun startSingleMonitor(target: String) {
        if (target.isBlank()) {
            _state.update { it.copy(error = "Masukkan IP target untuk monitor") }
            return
        }
        val ip = NetworkUtils.resolveDomain(target) ?: target
        _state.update { it.copy(isScanning = true, scanType = ScanType.MONITOR, error = null,
            summary = "Monitoring $ip...", summaryColor = 0xFF00695C, isSummaryOk = true,
            monitor = MonitorState(isRunning = true)) }
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                val probe = withContext(Dispatchers.IO) { PingUtil.pingProbe(ip) }
                val online = probe != null
                val wasOnline = _state.value.monitor.statuses[ip]
                if (online && ip in _favorites && wasOnline == false) notifyFavoriteBackOnline(ip)
                recordUptime(ip, online)
                probe?.let { recordPing(ip, it.latencyMs) }
                _state.update {
                    it.copy(monitor = MonitorState(isRunning = true, statuses = mapOf(ip to online)),
                        summary = if (online) "ping $ip — ${probe!!.latencyMs}ms" else "ping $ip — ✗",
                        summaryColor = if (online) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = online)
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
            SortMode.NAMA -> _hosts.values.sortedBy { it.label ?: it.hostname ?: it.ip }
            SortMode.UPTIME -> _hosts.values.sortedByDescending { uptimeOnlinePct(it.ip) }
        }
        // Favorit selalu di-pin di atas; urutan internal tetap stabil
        val pinned = sorted.sortedBy { it.ip !in _favorites }
        _state.update { it.copy(hosts = pinned) }
    }

    /** Persentase online dari riwayat ketersediaan (untuk sortir Uptime). */
    private fun uptimeOnlinePct(ip: String): Int {
        val events = _uptime[ip] ?: return 0
        if (events.isEmpty()) return 0
        return events.count { it.online } * 100 / events.size
    }

    /** Ping satu host: perbarui latency di kartu + riwayat grafik ping. */
    fun pingHost(ip: String) {
        viewModelScope.launch {
            _state.update { it.copy(summary = "ping $ip...", summaryColor = 0xFF00695C, isSummaryOk = true) }
            val probe = withContext(Dispatchers.IO) { PingUtil.pingProbe(ip) }
            val host = _hosts[ip]
            if (probe != null && host != null) {
                _hosts[ip] = host.copy(latencyMs = probe.latencyMs)
                recordPing(ip, probe.latencyMs)
                _state.update {
                    it.copy(hosts = _hosts.values.toList(),
                        summary = "ping $ip — ${probe.latencyMs}ms",
                        summaryColor = 0xFF2E7D32, isSummaryOk = true)
                }
            } else if (host != null) {
                _state.update {
                    it.copy(summary = "ping $ip — ✗ tidak merespons",
                        summaryColor = 0xFFC62828, isSummaryOk = false)
                }
            }
        }
    }

    private fun recordPing(ip: String, latencyMs: Long) {
        _pingHistory = PingStore.record(getApplication(), _pingHistory, ip, latencyMs)
        _state.update { it.copy(pingHistory = _pingHistory) }
    }

    /** Cari nama host via reverse DNS (PTR) untuk host yang hostname-nya kosong. */
    fun resolveHostname(ip: String) {
        viewModelScope.launch {
            _state.update { it.copy(summary = "Reverse DNS $ip...", summaryColor = 0xFF00695C, isSummaryOk = true) }
            val name = withContext(Dispatchers.IO) { ScanLoop.hostname(ip) }
            val host = _hosts[ip] ?: return@launch
            _hosts[ip] = host.copy(hostname = name ?: host.hostname)
            _state.update {
                it.copy(hosts = _hosts.values.toList(),
                    summary = if (name != null) "Hostname $ip → $name" else "Hostname $ip tidak ditemukan",
                    summaryColor = if (name != null) 0xFF2E7D32 else 0xFFC62828,
                    isSummaryOk = name != null)
            }
            persistResults(force = true)
        }
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
                if (host != null) {
                    val ports = host.openPorts.size
                    recordUptime(ip, host.isAlive)
                    host.latencyMs?.let { recordPing(ip, it) }
                    val existing = _hosts[ip]
                    val conflict = existing?.macAddress != null && host.macAddress != null &&
                        !existing.macAddress.equals(host.macAddress, ignoreCase = true)
                    _hosts[ip] = host.copy(label = existing?.label,
                        ipConflict = conflict || existing?.ipConflict == true)
                    _state.update {
                        it.copy(hosts = _hosts.values.toList(),
                            summary = when {
                                ports > 0 -> "Rescan $ip: $ports port terbuka"
                                host.isAlive -> "Rescan $ip: hidup, tidak ada port terbuka"
                                else -> "Rescan $ip: tidak merespons"
                            },
                            summaryColor = if (host.isAlive) 0xFF2E7D32 else 0xFFC62828,
                            isSummaryOk = host.isAlive)
                    }
                    persistResults(force = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Rescan $ip gagal") }
            }
        }
    }

    /** Mulai scan subnet /24 di sekitar satu IP (satu ketukan dari kartu host). */
    fun expandScanFromHost(ip: String) {
        if (_state.value.isScanning || ip.isBlank()) return
        val parts = ip.split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull()?.let { n -> n in 0..255 } != true }) return
        val target = "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
        _state.update { it.copy(target = target) }
        startScan(ScanType.PORT_SCAN)
    }

    fun clearResults() {
        _hosts.clear(); _urls.clear(); _lastDeleted = emptyList()
        _state.update {
            it.copy(hosts = emptyList(), discoveredUrls = emptyList(), hostSummary = "", scanResult = null,
                selectedHosts = emptySet(), searchQuery = "", deviceFilter = DeviceFilter.ALL,
                statusFilter = HostStatusFilter.ALL,
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


    fun toggleSettings() {
        _state.update { it.copy(showSettings = !it.showSettings) }
    }

    /** Set tema: null = sistem, false = terang, true = gelap (tersimpan). */
    fun setTheme(dark: Boolean?) {
        _state.update { it.copy(isDarkTheme = dark) }
        persistSettings()
    }

    fun setNotifyNewDevices(enabled: Boolean) {
        _state.update { it.copy(notifyNewDevices = enabled) }
        persistSettings()
    }

    fun setNotifyImportantOffline(enabled: Boolean) {
        _state.update { it.copy(notifyImportantOffline = enabled) }
        persistSettings()
    }

    fun setNotifyScanDone(enabled: Boolean) {
        _state.update { it.copy(notifyScanDone = enabled) }
        persistSettings()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _state.update { it.copy(keepScreenOn = enabled) }
        persistSettings()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(soundEnabled = enabled) }
        persistSettings()
    }

    fun setAutoDiffDialog(enabled: Boolean) {
        _state.update { it.copy(autoDiffDialog = enabled) }
        persistSettings()
    }

    fun setCompactMode(enabled: Boolean) {
        _state.update { it.copy(compactMode = enabled) }
        persistSettings()
    }

    /** Tandai dialog perubahan antar scan sudah ditampilkan (dibuka dari UI). */
    fun diffDialogShown() {
        _state.update { it.copy(openDiffDialog = false) }
    }

    private fun persistSettings() {
        val s = _state.value
        SettingsStore.save(getApplication(), AppSettings(
            darkTheme = s.isDarkTheme,
            notifyNewDevices = s.notifyNewDevices,
            notifyImportantOffline = s.notifyImportantOffline,
            notifyScanDone = s.notifyScanDone,
            keepScreenOn = s.keepScreenOn,
            soundEnabled = s.soundEnabled,
            autoDiffDialog = s.autoDiffDialog,
            compactMode = s.compactMode,
            monitorFavoritesOnly = s.monitorFavoritesOnly
        ))
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
        val printers = result.hosts.count { DeviceKind.PRINTER in it.deviceKinds() }
        val nas = result.hosts.count { DeviceKind.NAS in it.deviceKinds() }
        val tvs = result.hosts.count { DeviceKind.TV in it.deviceKinds() }
        val iots = result.hosts.count { DeviceKind.IOT in it.deviceKinds() }
        val phones = result.hosts.count { DeviceKind.PHONE in it.deviceKinds() }
        val parts = mutableListOf<String>()
        if (cameras > 0) parts.add("📷 $cameras")
        if (routers > 0) parts.add("🌐 $routers")
        if (shares > 0) parts.add("📁 $shares")
        if (printers > 0) parts.add("🖨️ $printers")
        if (nas > 0) parts.add("💾 $nas")
        if (tvs > 0) parts.add("📺 $tvs")
        if (iots > 0) parts.add("💡 $iots")
        if (phones > 0) parts.add("📱 $phones")
        return parts.joinToString("  ")
    }

    /** Notifikasi perangkat baru, di-throttle (maks 20/scan, min 2 detik antar notif). */
    private fun notifyNewDevice(host: HostInfo) {
        if (!_state.value.notifyNewDevices) return
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 2_000 || notifyCount >= MAX_NOTIFY_PER_SCAN) return
        lastNotifyAt = now; notifyCount++
        try {
            val ctx = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
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

    /** Mulai foreground service penjaga proses selama scan berjalan. */
    private fun startScanService() {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, ScanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        } catch (_: Exception) { }
    }

    /** Hentikan foreground service + hapus notifikasi progress. */
    private fun stopScanService() {
        try {
            val ctx = getApplication<Application>()
            ctx.stopService(Intent(ctx, ScanService::class.java))
            NotificationManagerCompat.from(ctx).cancel(ScanService.NOTIFICATION_ID)
        } catch (_: Exception) { }
    }

    /** Kirim/update notifikasi progress (throttle 1 detik di pemanggil). */
    private fun postProgressNotification(title: String, text: String, pct: Int) {
        try {
            val ctx = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(ScanService.CHANNEL_ID, "Scan Berjalan", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val notification = NotificationCompat.Builder(ctx, ScanService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, pct, pct == 0)
                .build()
            nm.notify(ScanService.NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    /** Perbarui notifikasi progress scan biasa (throttle 1 detik). */
    private fun updateScanNotification(event: ScanEvent.Progress) {
        val now = System.currentTimeMillis()
        if (now - lastScanNotifAt < 1_000) return
        lastScanNotifAt = now
        val pct = if (event.total > 0) event.current * 100 / event.total else 0
        val eta = estimateEta(event.current, event.total)
        postProgressNotification("NetRadar — scan berjalan ($pct%)", "${event.ip}$eta", pct)
    }

    /** Notifikasi ringkas saat scan selesai (hanya jika app di background). */
    private fun postScanDoneNotification(title: String, text: String) {
        if (AppForeground.isForeground || !_state.value.notifyScanDone) return
        try {
            val ctx = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(ScanService.CHANNEL_ID, "Scan Berjalan", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val notification = NotificationCompat.Builder(ctx, ScanService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(ScanService.NOTIFICATION_DONE_ID, notification)
        } catch (_: Exception) { }
    }

    /**
     * Perbarui daftar host di UI secara throttled (maks ~6x/detik) saat scan
     * berjalan, agar tidak menyalin + mengurutkan list tiap host ditemukan.
     */
    private fun refreshHostsUi(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHostUiAt < 150) return
        lastHostUiAt = now
        if (_hosts.size <= 500) applySort()
        else _state.update { it.copy(hosts = _hosts.values.toList()) }
    }

    /** Simpan satu entri riwayat scan (state + disk, IO di background). */
    private fun recordHistory(type: String, target: String, hostCount: Int, portCount: Int, durationMs: Long) {
        val entry = ScanHistoryEntry(System.currentTimeMillis(), type, target, hostCount, portCount, durationMs)
        _history = ScanHistoryStore.record(_history, entry)
        _state.update { it.copy(scanHistory = _history) }
        val app = getApplication<Application>()
        val history = _history
        viewModelScope.launch(Dispatchers.IO) { ScanHistoryStore.save(app, history) }
    }

    /** Pantau status gateway (online/offline + latency) tiap 5 detik. */
    private fun startGatewayMonitor() {
        gatewayJob?.cancel()
        gatewayJob = viewModelScope.launch {
            while (isActive) {
                if (AppForeground.isForeground) {
                    checkGateway()
                    checkInternet()
                    updateNetworkQuality()
                    delay(5_000)
                } else {
                    // App di background: jeda lama biar hemat baterai
                    delay(60_000)
                }
            }
        }
    }

    private suspend fun checkGateway() {
        val gw = _state.value.networkInfo.gateway
        if (gw.isBlank()) {
            if (_state.value.gatewayOnline != null)
                _state.update { it.copy(gatewayOnline = null, gatewayLatencyMs = null) }
            return
        }
        val probe = withContext(Dispatchers.IO) { PingUtil.pingProbe(gw) }
        val st = _state.value
        if (probe != null) {
            gatewayLatencies.addLast(probe.latencyMs)
            while (gatewayLatencies.size > 12) gatewayLatencies.removeFirst()
            if (st.gatewayOnline != true || st.gatewayLatencyMs != probe.latencyMs)
                _state.update { it.copy(gatewayOnline = true, gatewayLatencyMs = probe.latencyMs) }
        } else {
            gatewayLatencies.clear()
            if (st.gatewayOnline != false)
                _state.update { it.copy(gatewayOnline = false, gatewayLatencyMs = null) }
        }
    }

    /** Cek koneksi internet: ping 1.1.1.1 lalu 8.8.8.8 (online jika salah satu membalas). */
    private suspend fun checkInternet() {
        val probe = withContext(Dispatchers.IO) {
            listOf("1.1.1.1", "8.8.8.8").firstNotNullOfOrNull { PingUtil.pingProbe(it) }
        }
        val st = _state.value
        if (probe != null) {
            if (st.internetOnline != true || st.internetLatencyMs != probe.latencyMs)
                _state.update { it.copy(internetOnline = true, internetLatencyMs = probe.latencyMs) }
        } else {
            if (st.internetOnline != false)
                _state.update { it.copy(internetOnline = false, internetLatencyMs = null) }
        }
    }

    /** Hitung label kualitas jaringan dari rata-rata latency gateway + jitter antar ping. */
    private fun updateNetworkQuality() {
        val st = _state.value
        val (label, color) = when {
            st.gatewayOnline == false -> "Gateway ✗" to 0xFFC62828L
            st.internetOnline == false -> "Internet ✗" to 0xFFE65100L
            gatewayLatencies.size < 2 -> "Mengukur…" to 0xFF00695CL
            else -> {
                val avg = gatewayLatencies.average()
                val jitter = (1 until gatewayLatencies.size)
                    .map { kotlin.math.abs(gatewayLatencies[it] - gatewayLatencies[it - 1]) }
                    .average()
                when {
                    avg < 15 && jitter < 8 -> "Stabil" to 0xFF2E7D32L
                    avg < 60 && jitter < 30 -> "Normal" to 0xFF00695CL
                    avg < 150 && jitter < 100 -> "Lemot" to 0xFFE65100L
                    else -> "Tidak stabil" to 0xFFC62828L
                }
            }
        }
        if (st.networkQualityLabel != label || st.networkQualityColor != color) {
            _state.update { it.copy(networkQualityLabel = label, networkQualityColor = color) }
        }
    }

    private fun persistResults(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 2_000) return  // throttle: max tiap 2 detik saat scan
        lastPersistAt = now
        val hosts = _hosts.values.toList()
        val urls = _urls.values.toList()
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { ResultsStore.save(app, hosts, urls) }
        // Widget tidak perlu di-refresh tiap host: cukup tiap 15 detik / saat selesai
        if (force || now - lastWidgetAt >= 15_000) {
            lastWidgetAt = now
            NetRadarWidget.pushUpdate(app)
        }
    }

    fun toggleFavorite(ip: String) {
        if (!_favorites.add(ip)) _favorites.remove(ip)
        FavoritesStore.save(getApplication(), _favorites)
        _state.update { it.copy(favoriteIps = _favorites.toSet()) }
        applySort()
        NetRadarWidget.pushUpdate(getApplication())
    }

    /** Deep scan semua port (1..65535) satu host, berjalan di background. */
    fun deepScanHost(ip: String) {
        if (_state.value.isScanning || _state.value.deepScanning != null) return
        val speed = _state.value.scanSpeed
        _state.update {
            it.copy(deepScanning = ip, deepScanProgress = 0,
                summary = "Deep scan $ip...", summaryColor = 0xFF00695C, isSummaryOk = true)
        }
        startScanService()
        val startedAt = System.currentTimeMillis()
        _deepScanJob?.cancel()
        _deepScanJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    portScanner.deepScan(ip, speed) { pct ->
                        // Update state harus di main thread: mengubah state dari thread
                        // background saat Compose sedang mengukur layout memicu
                        // ComposeRuntimeError (pending composition has not been applied).
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            _state.update { it.copy(deepScanProgress = pct) }
                            updateDeepScanNotification(ip, pct)
                        }
                    }
                }
                val ports = result.ports
                val existing = _hosts[ip]
                val host = (existing ?: HostInfo(ip)).copy(
                    openPorts = ports,
                    isAlive = true,
                    label = existing?.label,
                    isNew = false,
                    ipConflict = existing?.ipConflict == true
                )
                _hosts[ip] = host
                refreshHostsUi(force = true)
                if (_state.value.soundEnabled) SoundFeedback.playForPort(ports.firstOrNull()?.port ?: 0)
                recordUptime(ip, true)
                persistResults(force = true)
                recordHistory("Deep scan", ip, 1, ports.size, System.currentTimeMillis() - startedAt)
                stopScanService()
                _state.update {
                    it.copy(deepScanning = null, deepScanProgress = 0,
                        summary = "Deep scan $ip selesai: ${ports.size} port terbuka" +
                            if (result.truncated) " (dibatasi 4000, host merespons semua port)" else "",
                        summaryColor = 0xFF2E7D32, isSummaryOk = true)
                }
            } catch (e: CancellationException) {
                stopScanService()
                _state.update { it.copy(deepScanning = null, deepScanProgress = 0,
                    summary = "Deep scan dibatalkan", summaryColor = 0xFFC62828, isSummaryOk = false) }
                throw e
            } catch (_: Throwable) {
                stopScanService()
                _state.update { it.copy(deepScanning = null, deepScanProgress = 0,
                    error = "Deep scan $ip gagal") }
            }
        }
    }

    /** Batalkan deep scan yang sedang berjalan dari UI. */
    fun cancelDeepScan() {
        _deepScanJob?.cancel()
        _deepScanJob = null
        stopScanService()
        _state.update { it.copy(deepScanning = null, deepScanProgress = 0,
            summary = "Deep scan dibatalkan", summaryColor = 0xFFC62828, isSummaryOk = false) }
    }

    /** Perbarui notifikasi progress deep scan (throttle 1 detik). */
    private fun updateDeepScanNotification(ip: String, pct: Int) {
        val now = System.currentTimeMillis()
        if (now - lastScanNotifAt < 1_000) return
        lastScanNotifAt = now
        postProgressNotification("NetRadar — deep scan", ip, pct)
    }

    /** Set/ubah label (nama) perangkat. */
    fun setHostLabel(ip: String, label: String?) {
        val host = _hosts[ip] ?: return
        val clean = label?.trim()?.takeIf { it.isNotBlank() }
        _hosts[ip] = host.copy(label = clean)
        _state.update { it.copy(hosts = _hosts.values.toList()) }
        persistResults(force = true)
        NetRadarWidget.pushUpdate(getApplication())
    }

    private fun recordUptime(ip: String, online: Boolean) {
        _uptime = UptimeStore.record(getApplication(), _uptime, ip, online)
        _state.update { it.copy(uptime = _uptime) }
    }

    /** Notifikasi saat perangkat penting (favorit) offline. */
    private fun notifyImportantOffline(ip: String) {
        if (!_state.value.notifyImportantOffline) return
        try {
            val ctx = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_IMPORTANT, "Perangkat Penting", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val notification = NotificationCompat.Builder(ctx, CHANNEL_IMPORTANT)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("⚠ Perangkat penting offline: $ip")
                .setContentText("Perangkat favorit tidak merespons ping.")
                .setAutoCancel(true)
                .build()
            nm.notify(ip.hashCode() + 9999, notification)
        } catch (_: Exception) { }
    }

    /** Notifikasi saat perangkat penting yang tadinya offline kembali online. */
    private fun notifyFavoriteBackOnline(ip: String) {
        if (!_state.value.notifyImportantOffline) return
        val now = System.currentTimeMillis()
        if (now - lastBackOnlineAt < 30_000) return
        lastBackOnlineAt = now
        try {
            val ctx = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val nm = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_IMPORTANT, "Perangkat Penting", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val name = _hosts[ip]?.label ?: _hosts[ip]?.hostname?.takeIf { it != ip } ?: "Perangkat"
            val notification = NotificationCompat.Builder(ctx, CHANNEL_IMPORTANT)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("📱 $name kembali online")
                .setContentText("$ip merespons ping lagi.")
                .setAutoCancel(true)
                .build()
            nm.notify(ip.hashCode() + 7777, notification)
        } catch (_: Exception) { }
    }

    /** Bandingkan host yang ditemukan scan ini vs scan sebelumnya. */
    private fun computeDiff() {
        val current = _foundThisScan
        val previous = _previousScanHosts ?: emptyMap()
        val diff = ScanDiff.compute(current, previous) { _hosts[it] }
        _previousScanHosts = current
        _state.update { it.copy(diff = diff) }
    }

    private fun estimateEta(current: Int, total: Int): String {
        if (total <= 0 || current <= 0) return ""
        val elapsedMs = System.currentTimeMillis() - _startTime
        if (elapsedMs <= 0) return ""
        // Kecepatan aktual (IP/detik) dari progres yang sudah lewat
        val rate = current.toDouble() * 1000 / elapsedMs
        val remainSec = ((total - current) / rate).toInt()
        val m = remainSec / 60
        val s = remainSec % 60
        return " · sisa ±${m}m ${s}s"
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stop()
        monitorJob?.cancel()
        gatewayJob?.cancel()
        _deepScanJob?.cancel()
        stopScanService()
        ScanCheckpointStore.save(getApplication(), _checkpoint)  // sinkron: viewModelScope sudah batal
        ResultsStore.save(getApplication(), _hosts.values.toList(), _urls.values.toList())
        ResultsStore.saveScanCount(getApplication(), _scanCount)
    }
}
