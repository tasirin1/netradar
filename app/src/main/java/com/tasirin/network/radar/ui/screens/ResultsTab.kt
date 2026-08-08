package com.tasirin.network.radar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.*
import com.tasirin.network.radar.ui.theme.*
import com.tasirin.network.radar.viewmodel.ScanUiState
import kotlinx.coroutines.launch

/** Halaman Hasil: input target, tombol scan, progres, dan daftar host/URL. */
@Composable
fun ResultsTab(
    state: ScanUiState,
    snackbarHostState: SnackbarHostState,
    onOpenTab: (MainTab) -> Unit,
    onTargetChange: (String) -> Unit,
    onScan: (ScanType) -> Unit,
    onStop: () -> Unit,
    onPauseResume: (() -> Unit)? = null,
    onCopyIp: ((String) -> Unit)? = null,
    onToggleHostSelect: ((String) -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onUndoDelete: (() -> Unit)? = null,
    onSelectAllHosts: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    onSortMode: ((SortMode) -> Unit)? = null,
    scanSpeed: ScanSpeed = ScanSpeed.SEDANG,
    onSelectScanSpeed: ((ScanSpeed) -> Unit)? = null,
    onSearchChange: ((String) -> Unit)? = null,
    onDeviceFilter: ((DeviceFilter) -> Unit)? = null,
    onStatusFilter: ((HostStatusFilter) -> Unit)? = null,
    onRescanHost: ((String) -> Unit)? = null,
    onSelectInterface: ((String) -> Unit)? = null,
    onToggleFavorite: ((String) -> Unit)? = null,
    onDeepScan: ((String) -> Unit)? = null,
    onCancelDeepScan: (() -> Unit)? = null,
    onPingHost: ((String) -> Unit)? = null,
    onExpandScan: ((String) -> Unit)? = null,
    onResolveHostname: ((String) -> Unit)? = null,
    onSetHostLabel: ((String, String?) -> Unit)? = null,
    onDiffDialogShown: (() -> Unit)? = null,
    onConfirmWideScan: (() -> Unit)? = null,
    onCancelWideScan: (() -> Unit)? = null,
    onResumeScan: (() -> Unit)? = null,
    onClearCheckpoint: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var detailHost by remember { mutableStateOf<HostInfo?>(null) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var showMapDialog by remember { mutableStateOf(false) }
    var showMatrixDialog by remember { mutableStateOf(false) }
    var showDashboard by remember { mutableStateOf(false) }
    var showConstellation by remember { mutableStateOf(false) }
    var showVizDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Buka dialog perubahan antar scan otomatis saat scan selesai (opsi pengaturan)
    LaunchedEffect(state.openDiffDialog) {
        if (state.openDiffDialog) {
            showDiffDialog = true
            onDiffDialogShown?.invoke()
        }
    }




    // Detail host: OS, favorit, riwayat ketersediaan
    detailHost?.let { host ->
        HostDetailDialog(
            host = host,
            isFavorite = host.ip in state.favoriteIps,
            uptime = state.uptime[host.ip] ?: emptyList(),
            pingHistory = state.pingHistory[host.ip] ?: emptyList(),
            onToggleFavorite = { onToggleFavorite?.invoke(host.ip) },
            onSetLabel = { label -> onSetHostLabel?.invoke(host.ip, label) },
            onDeepScan = { onDeepScan?.invoke(host.ip) },
            onResolveHostname = { onResolveHostname?.invoke(host.ip) },
            onExpandScan = { onExpandScan?.invoke(host.ip) },
            onCopyIp = { onCopyIp?.invoke(host.ip) },
            onWol = host.macAddress?.let { mac -> { onWol?.invoke(host.ip, mac) } },
            onRescan = { onRescanHost?.invoke(host.ip) },
            onPing = { onPingHost?.invoke(host.ip) },
            onDismiss = { detailHost = null }
        )
    }

    // Perubahan antar scan
    if (showDiffDialog) {
        ScanDiffDialog(
            diff = state.diff ?: ScanDiff(),
            onDismiss = { showDiffDialog = false }
        )
    }


    // Dashboard ambient (monitor mode)
    if (showDashboard) {
        AmbientDashboard(
            monitor = state.monitor,
            gateway = state.networkInfo.gateway,
            onDismiss = { showDashboard = false }
        )
    }

    // Rasi bintang jaringan
    if (showConstellation) {
        ConstellationDialog(
            hosts = state.hosts,
            gateway = state.networkInfo.gateway,
            onDismiss = { showConstellation = false }
        )
    }

    // Pilihan visualisasi (Peta / Matriks / Rasi bintang)
    if (showVizDialog) {
        VisualizationDialog(
            onMap = { showVizDialog = false; showMapDialog = true },
            onMatrix = { showVizDialog = false; showMatrixDialog = true },
            onConstellation = { showVizDialog = false; showConstellation = true },
            onDismiss = { showVizDialog = false }
        )
    }

    // Filter jenis perangkat + status (dipindah dari halaman utama)
    if (showFilterDialog) {
        HostFilterDialog(
            deviceFilter = state.deviceFilter,
            onDeviceFilter = { onDeviceFilter?.invoke(it) },
            statusFilter = state.statusFilter,
            onStatusFilter = { onStatusFilter?.invoke(it) },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Pilihan sensitivitas scan (detail angka disembunyikan di halaman utama)
    if (showSpeedDialog) {
        ScanSpeedDialog(
            current = state.scanSpeed,
            onSelect = { onSelectScanSpeed?.invoke(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Konfirmasi scan area luas (banyak subnet)
    state.pendingWideTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onCancelWideScan?.invoke() },
            title = { Text("Scan area luas?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Target '$target' mencakup ±${state.pendingWideCount} IP " +
                        "tersebar di banyak subnet.")
                    Spacer(Modifier.height(6.dp))
                    Text("Scan bisa berlangsung lama — makin rendah level sensitivitas, makin teliti tapi lambat.",
                        fontSize = 11.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirmWideScan?.invoke() }) {
                    Text("Lanjutkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onCancelWideScan?.invoke() }) { Text("Batal") }
            }
        )
    }

    if (showMapDialog) {
        NetworkMapDialog(
            hosts = state.hosts,
            gateway = state.networkInfo.gateway,
            onHostClick = { host ->
                showMapDialog = false
                detailHost = host
            },
            onDismiss = { showMapDialog = false }
        )
    }

    if (showMatrixDialog) {
        PortMatrixDialog(
            hosts = state.hosts,
            onDismiss = { showMatrixDialog = false }
        )
    }

    val filteredHosts = remember(state.hosts, state.searchQuery, state.deviceFilter, state.statusFilter,
        state.uptime, state.staleIps) {
        val q = state.searchQuery.trim()
        state.hosts.filter { host ->
            val okQuery = q.isEmpty() || host.ip.contains(q, true) ||
                host.label?.contains(q, true) == true ||
                host.hostname?.contains(q, true) == true ||
                host.macAddress?.contains(q, true) == true ||
                host.macVendor?.contains(q, true) == true ||
                host.openPorts.any { it.port.toString().contains(q) || it.service?.contains(q, true) == true }
            val okFilter = when (state.deviceFilter) {
                DeviceFilter.ALL -> true
                DeviceFilter.CAMERA -> DeviceKind.CAMERA in host.deviceKinds()
                DeviceFilter.ROUTER -> DeviceKind.ROUTER in host.deviceKinds()
                DeviceFilter.SHARE -> DeviceKind.SHARE in host.deviceKinds()
                DeviceFilter.PRINTER -> DeviceKind.PRINTER in host.deviceKinds()
                DeviceFilter.NAS -> DeviceKind.NAS in host.deviceKinds()
                DeviceFilter.TV -> DeviceKind.TV in host.deviceKinds()
                DeviceFilter.IOT -> DeviceKind.IOT in host.deviceKinds()
                DeviceFilter.PHONE -> DeviceKind.PHONE in host.deviceKinds()
            }
            val lastOnline = state.uptime[host.ip]?.lastOrNull()?.online
            val okStatus = when (state.statusFilter) {
                HostStatusFilter.ALL -> true
                HostStatusFilter.ONLINE -> lastOnline == true
                HostStatusFilter.OFFLINE -> lastOnline == false
                HostStatusFilter.LAMA -> host.ip in state.staleIps
            }
            okQuery && okFilter && okStatus
        }
    }

        LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ─── Jaringan ───
        item { SectionHeader("Jaringan") }
        item {
            Column {
                NetworkInfoBar(
                    localIp = state.networkInfo.localIp,
                    gateway = state.networkInfo.gateway,
                    subnet = state.networkInfo.subnet,
                    interfaces = state.networkInfo.availableInterfaces,
                    selectedInterface = state.networkInfo.selectedInterface,
                    onSelectInterface = onSelectInterface,
                    gatewayOnline = state.gatewayOnline,
                    gatewayLatencyMs = state.gatewayLatencyMs,
                    internetOnline = state.internetOnline,
                    internetLatencyMs = state.internetLatencyMs,
                    networkQualityLabel = state.networkQualityLabel,
                    networkQualityColor = state.networkQualityColor
                )
                if (state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    NetworkSummaryBar(
                        hostCount = state.hosts.size,
                        onlineCount = if (state.monitor.isRunning && state.monitor.statuses.isNotEmpty())
                            state.monitor.statuses.values.count { it }
                        else state.hosts.count { state.uptime[it.ip]?.lastOrNull()?.online == true },
                        newCount = state.hosts.count { it.isNew },
                        portCount = state.hosts.sumOf { it.openPorts.size },
                        conflictCount = state.hosts.count { it.ipConflict },
                        urlCount = state.discoveredUrls.size
                    )
                }
            }
        }

        // ─── Pemindaian ───
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Pemindaian")
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TargetInput(
                    value = state.target,
                    onValueChange = onTargetChange,
                    hint = "IP, URL, atau CIDR",
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { showSpeedDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Speed, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(state.scanSpeed.label, fontSize = 11.sp)
                }
            }
        }
        // Banner lanjutkan scan terakhir (posisi tersimpan saat stop / app ditutup)
        if (state.canResumeScan && !state.isScanning) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(state.resumeInfo, fontSize = 10.sp, color = TextSecondary,
                            modifier = Modifier.weight(1f), maxLines = 2)
                        TextButton(
                            onClick = { onResumeScan?.invoke() },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Lanjutkan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onClearCheckpoint?.invoke() },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Mulai baru", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
        // Chip target terakhir agar cepat mengulang scan
        if (state.recentTargets.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.recentTargets.forEach { target ->
                        AssistChip(
                            onClick = { onTargetChange(target) },
                            label = { Text(target, fontSize = 10.sp, maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = TextSecondary)
                            }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }

        // ─── Scan Buttons ───
        item { ScanButtonRow(isScanning = state.isScanning, onScan = onScan) }
        item { Spacer(Modifier.height(6.dp)) }

        // ─── Status & progress (digabung dengan area pemindaian) ───
        item {
            Column {
                StatusBar(
                    text = state.summary,
                    isOk = state.isSummaryOk,
                    isScanning = state.isScanning,
                    progress = state.progress,
                    progressPercent = state.progressPercent
                )
                if (state.deepScanning != null) {
                    Column(Modifier.padding(top = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Deep scan ${state.deepScanning}... ${state.deepScanProgress}%",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (onCancelDeepScan != null) {
                                TextButton(
                                    onClick = onCancelDeepScan,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Batal", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = state.deepScanProgress / 100f,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }
                }
                if (state.copyFeedback != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.copyFeedback, fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                }
                if (state.hostSummary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.hostSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }

        // ─── Aksi: Pause/Stop + Riwayat (satu baris senada) ───
        item {
            ScanActionRow(
                isScanning = state.isScanning,
                onStop = onStop,
                isPaused = state.isPaused,
                onPauseResume = onPauseResume,
                onHistory = { onOpenTab(MainTab.RIWAYAT) }
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        // ─── Hasil ───
        if (state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Hasil")
                }
            }
        }
        if (state.hosts.isNotEmpty()) item { Spacer(Modifier.height(6.dp)) }

        // ─── Monitor ───
        if (state.scanType == ScanType.MONITOR && state.monitor.isRunning) {
            item { MonitorDisplay(state.monitor, onDashboard = { showDashboard = true }) }
            item { Spacer(Modifier.height(6.dp)) }
        }

        // ─── Selection bar ───
        if (state.selectedHosts.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${state.selectedHosts.size} dipilih", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectAllHosts?.invoke() },
                        contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Semua", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            val n = state.selectedHosts.size
                            onDeleteSelected?.invoke()
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "Hapus $n host",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) onUndoDelete?.invoke()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Hapus (${state.selectedHosts.size})", fontSize = 12.sp) }
                    TextButton(onClick = { onClearSelection?.invoke() },
                        contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Batal", fontSize = 12.sp) }
                }
            }
        }

        // ─── Results - Hosts ───
        if (state.hosts.isNotEmpty()) {
            item {
                Column {
                    val filterActive =
                        state.deviceFilter != DeviceFilter.ALL || state.statusFilter != HostStatusFilter.ALL
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (filteredHosts.size == state.hosts.size) "Hosts (${state.hosts.size})"
                            else "Hosts (${filteredHosts.size}/${state.hosts.size})",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showFilterDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FilterList, null, Modifier.size(18.dp),
                                tint = if (filterActive) MaterialTheme.colorScheme.primary else TextSecondary)
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Sort, null, Modifier.size(18.dp), tint = TextSecondary)
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label, fontSize = 12.sp) },
                                        trailingIcon = if (state.sortMode == mode) {
                                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = { sortMenuOpen = false; onSortMode?.invoke(mode) }
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { showVizDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("🪐 Visualisasi", fontSize = 11.sp) }
                    }
                    if (filterActive) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                if (state.deviceFilter != DeviceFilter.ALL) append(state.deviceFilter.label)
                                if (state.statusFilter != HostStatusFilter.ALL) {
                                    if (isNotEmpty()) append(" · ")
                                    append(state.statusFilter.label)
                                }
                            },
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = TextSecondary)
                            Spacer(Modifier.width(6.dp))
                            BasicTextField(
                                value = state.searchQuery,
                                onValueChange = { onSearchChange?.invoke(it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    if (state.searchQuery.isEmpty()) {
                                        Text("Cari IP / nama / MAC / port", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    inner()
                                }
                            )
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange?.invoke("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }

            if (filteredHosts.isEmpty()) {
                item {
                    Text("Tidak ada host yang cocok", color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(filteredHosts, key = { it.ip }) { host ->
                    HostCard(
                        host = host,
                        isStale = host.ip in state.staleIps,
                        pingHistory = state.pingHistory[host.ip] ?: emptyList(),
                        compact = state.compactMode,
                        isFavorite = host.ip in state.favoriteIps,
                        onToggleFavorite = { onToggleFavorite?.invoke(host.ip) },
                        onShowDetail = { detailHost = host },
                        onDeepScan = if (onDeepScan == null) null else ({ onDeepScan(host.ip) }),
                        isDeepScanBusy = state.deepScanning != null,
                        isDeepScanning = state.deepScanning == host.ip,
                        isSelected = host.ip in state.selectedHosts,
                        selectionMode = state.selectedHosts.isNotEmpty(),
                        onToggleSelect = { onToggleHostSelect?.invoke(host.ip) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ─── Results - URLs ───
        if (state.discoveredUrls.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text("URLs (${state.discoveredUrls.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(state.discoveredUrls, key = { it.url }) { url ->
                UrlCard(url)
                Spacer(Modifier.height(4.dp))
            }
        }

        // ─── Empty state ───
        if (state.hosts.isEmpty() && state.discoveredUrls.isEmpty() && !state.isScanning && state.scanResult != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Tidak ada hasil ditemukan", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

    }
}
