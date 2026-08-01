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
import com.tasirin.network.radar.scanner.ScannerManager
import com.tasirin.network.radar.util.FavoritesStore
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import com.tasirin.network.radar.util.ResultsStore
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
    val networkInfo: NetworkInfo = NetworkInfo(),
    val sortMode: SortMode = SortMode.IP,
    val monitor: MonitorState = MonitorState(),
    val copyFeedback: String? = null,
    val favoriteIps: Set<String> = emptySet(),
    val uptime: Map<String, List<UptimeEvent>> = emptyMap(),
    val diff: ScanDiff? = null
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
    private var scanGeneration = 0L
    private var lastScanNotifAt = 0L
    private var lastFavoriteAlertAt = 0L
    private var _favorites = mutableSetOf<String>()
    private var _uptime = emptyMap<String, List<UptimeEvent>>()
    private var _foundThisScan = linkedMapOf<String, List<Int>>()
    private var _previousScanHosts: Map<String, List<Int>>? = null

    private companion object {
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val CHANNEL_IMPORTANT = "important_device"
        const val MAX_NOTIFY_PER_SCAN = 20
    }

    init {
        refreshNetworkInfo()
        // Muat hasil dari sesi sebelumnya agar tidak hilang saat app ditutup
        val (hosts, urls) = ResultsStore.load(getApplication())
        hosts.forEach { _hosts[it.ip] = it }
        urls.forEach { _urls[it.url] = it }
        _favorites = FavoritesStore.load(getApplication()).toMutableSet()
        _uptime = UptimeStore.load(getApplication())
        _state.update {
            it.copy(
                hosts = _hosts.values.toList(),
                discoveredUrls = _urls.values.toList(),
                favoriteIps = _favorites.toSet(),
                uptime = _uptime,
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
                            updateScanNotification(event)
                        }
                        is ScanEvent.HostFound -> {
                            _foundThisScan[event.host.ip] = event.host.openPorts.map { it.port }
                            val isNew = !_hosts.containsKey(event.host.ip)
                            val host = if (isNew) event.host.copy(isNew = true) else event.host
                            _hosts[host.ip] = host
                            if (_hosts.size <= 500) applySort()
                            else _state.update { it.copy(hosts = _hosts.values.toList()) }
                            if (isNew && type != ScanType.TRACE) notifyNewDevice(host)
                            recordUptime(event.host.ip, true)
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
                            applySort()
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
                            _state.update { it.copy(isScanning = false, isPaused = false, scanType = null,
                                progress = "", progressPercent = 1f, summary = finalSummary,
                                summaryColor = if (ok) 0xFF2E7D32 else 0xFFC62828, isSummaryOk = ok,
                                hostSummary = buildHostSummary(result), scanResult = result) }
                            if (gen == scanGeneration) stopScanService()
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                if (gen == scanGeneration) stopScanService()
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
        stopScanService()
        persistResults(force = true)
        _state.update { it.copy(isScanning = false, isPaused = false, summary = "Stopped",
            summaryColor = 0xFFC62828, isSummaryOk = false, monitor = MonitorState(), selectedHosts = emptySet()) }
    }

    private fun startMonitor(target: String) {
        val hosts = _hosts.values.toList().sortedBy { it.ip }
        if (hosts.isEmpty()) {
            startSingleMonitor(target)
            return
        }
        val ips = hosts.map { it.ip }
        _state.update { it.copy(isScanning = true, scanType = ScanType.MONITOR, error = null,
            summary = "Monitoring ${ips.size} perangkat...", summaryColor = 0xFF00695C, isSummaryOk = true,
            monitor = MonitorState(isRunning = true)) }
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            var pings = 0
            while (isActive) {
                val statuses = ips.chunked(8).flatMap { chunk ->
                    chunk.map { ip -> async { ip to (PingUtil.pingProbe(ip) != null) } }.map { it.await() }
                }.toMap()
                pings++
                statuses.forEach { (ip, online) ->
                    recordUptime(ip, online)
                    if (!online && ip in _favorites &&
                        System.currentTimeMillis() - lastFavoriteAlertAt > 30_000) {
                        lastFavoriteAlertAt = System.currentTimeMillis()
                        notifyImportantOffline(ip)
                    }
                }
                val onlineCount = statuses.values.count { it }
                _state.update {
                    it.copy(monitor = MonitorState(isRunning = true, statuses = statuses, pings = pings),
                        summary = "Monitor: $onlineCount/${statuses.size} online",
                        summaryColor = if (onlineCount > 0) 0xFF2E7D32 else 0xFFC62828,
                        isSummaryOk = onlineCount > 0)
                }
                delay(3000)
            }
        }
    }

    private fun startSingleMonitor(target: String) {
        val ip = NetworkUtils.resolveDomain(target) ?: target
        _state.update { it.copy(isScanning = true, scanType = ScanType.MONITOR, error = null,
            summary = "Monitoring $ip...", summaryColor = 0xFF00695C, isSummaryOk = true,
            monitor = MonitorState(isRunning = true)) }
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                val probe = PingUtil.pingProbe(ip)
                val online = probe != null
                recordUptime(ip, online)
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
                recordUptime(ip, host != null)
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

    /** Perbarui notifikasi progress scan (throttle 1 detik). */
    private fun updateScanNotification(event: ScanEvent.Progress) {
        val now = System.currentTimeMillis()
        if (now - lastScanNotifAt < 1_000) return
        lastScanNotifAt = now
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
            val pct = if (event.total > 0) event.current * 100 / event.total else 0
            val notification = NotificationCompat.Builder(ctx, ScanService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("NetRadar — scan berjalan")
                .setContentText(event.ip)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, pct, pct == 0)
                .build()
            nm.notify(ScanService.NOTIFICATION_ID, notification)
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
        NetRadarWidget.pushUpdate(app, hosts.size, _favorites.size)
    }

    fun toggleFavorite(ip: String) {
        if (!_favorites.add(ip)) _favorites.remove(ip)
        FavoritesStore.save(getApplication(), _favorites)
        _state.update { it.copy(favoriteIps = _favorites.toSet()) }
        NetRadarWidget.pushUpdate(getApplication(), _hosts.size, _favorites.size)
    }

    private fun recordUptime(ip: String, online: Boolean) {
        _uptime = UptimeStore.record(getApplication(), _uptime, ip, online)
        _state.update { it.copy(uptime = _uptime) }
    }

    /** Notifikasi saat perangkat penting (favorit) offline. */
    private fun notifyImportantOffline(ip: String) {
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

    /** Bandingkan host yang ditemukan scan ini vs scan sebelumnya. */
    private fun computeDiff() {
        val current = _foundThisScan
        val previous = _previousScanHosts ?: emptyMap()
        val added = current.filterKeys { it !in previous }.keys.map { _hosts[it] ?: HostInfo(it) }
        val removed = previous.filterKeys { it !in current }.keys.mapNotNull { _hosts[it] }
        val changed = current.filterKeys { ip ->
            val prev = previous[ip]
            prev != null && prev.toSet() != current[ip]!!.toSet()
        }.keys.map { _hosts[it] ?: HostInfo(it) }
        _previousScanHosts = current
        _state.update { it.copy(diff = ScanDiff(added, removed, changed)) }
    }

    private fun estimateEta(current: Int, total: Int): String {
        if (total <= 0 || current <= 0) return ""
        val elapsed = System.currentTimeMillis() - _startTime
        val remainMs = (total - current).toDouble() * elapsed / current
        val remainSec = (remainMs / 1000).toInt()
        val m = remainSec / 60
        val s = remainSec % 60
        return " · sisa ±${m}m ${s}s"
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stop()
        monitorJob?.cancel()
        stopScanService()
        ResultsStore.save(getApplication(), _hosts.values.toList(), _urls.values.toList())
    }
}
