package com.tasirin.network.radar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.BuildConfig
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.*
import com.tasirin.network.radar.ui.theme.*
import com.tasirin.network.radar.viewmodel.ScanUiState
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: ScanUiState,
    onTargetChange: (String) -> Unit,
    onScan: (ScanType) -> Unit,
    onStop: () -> Unit,
    onPauseResume: (() -> Unit)? = null,
    onToggleTheme: () -> Unit,
    onCopyIp: ((String) -> Unit)? = null,
    onCopyAll: (() -> Unit)? = null,
    onToggleHostSelect: ((String) -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onUndoDelete: (() -> Unit)? = null,
    onSelectAllHosts: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onClearResults: (() -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    onSortMode: ((SortMode) -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    scanSpeed: ScanSpeed = ScanSpeed.SEDANG,
    onSelectScanSpeed: ((ScanSpeed) -> Unit)? = null,
    onSearchChange: ((String) -> Unit)? = null,
    onDeviceFilter: ((DeviceFilter) -> Unit)? = null,
    onRescanHost: ((String) -> Unit)? = null,
    onSelectInterface: ((String) -> Unit)? = null,
    onToggleFavorite: ((String) -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var detailHost by remember { mutableStateOf<HostInfo?>(null) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var showMapDialog by remember { mutableStateOf(false) }
    var showMatrixDialog by remember { mutableStateOf(false) }

    // About dialog
    if (state.showAbout) {
        AboutDialog(onDismiss = { onAbout?.invoke() })
    }

    // Konfirmasi hapus semua hasil
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus semua hasil?") },
            text = { Text("Semua host dan URL hasil scan akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearResults?.invoke()
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Detail host: OS, favorit, riwayat ketersediaan
    detailHost?.let { host ->
        HostDetailDialog(
            host = host,
            isFavorite = host.ip in state.favoriteIps,
            uptime = state.uptime[host.ip] ?: emptyList(),
            onToggleFavorite = { onToggleFavorite?.invoke(host.ip) },
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

    if (showMapDialog) {
        NetworkMapDialog(
            hosts = state.hosts,
            gateway = state.networkInfo.gateway,
            onDismiss = { showMapDialog = false }
        )
    }

    if (showMatrixDialog) {
        PortMatrixDialog(
            hosts = state.hosts,
            onDismiss = { showMatrixDialog = false }
        )
    }

    val filteredHosts = remember(state.hosts, state.searchQuery, state.deviceFilter) {
        val q = state.searchQuery.trim()
        state.hosts.filter { host ->
            val okQuery = q.isEmpty() || host.ip.contains(q, true) ||
                host.hostname?.contains(q, true) == true ||
                host.macAddress?.contains(q, true) == true
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
            okQuery && okFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NetRadar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Network Radar Scanner", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { onAbout?.invoke() }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = when (state.isDarkTheme) {
                                true -> Icons.Default.LightMode
                                false -> Icons.Default.DarkMode
                                null -> Icons.Default.SettingsBrightness
                            },
                            contentDescription = "Toggle theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // ─── Network Info ───
            if (state.networkInfo.localIp.isNotEmpty()) {
                item {
                    NetworkInfoBar(
                        localIp = state.networkInfo.localIp,
                        gateway = state.networkInfo.gateway,
                        subnet = state.networkInfo.subnet,
                        interfaces = state.networkInfo.availableInterfaces,
                        selectedInterface = state.networkInfo.selectedInterface,
                        onSelectInterface = onSelectInterface
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            // ─── Target Input ───
            item { TargetInput(value = state.target, onValueChange = onTargetChange, hint = "Target IP, URL, or CIDR") }

            // ─── Level Sensitivitas Scan ───
            item {
                Column {
                    Spacer(Modifier.height(4.dp))
                    // Level sensitivitas scan: host paralel + timeout koneksi
                    if (onSelectScanSpeed != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Speed", fontSize = 11.sp, color = TextSecondary)
                            ScanSpeed.entries.forEach { speed ->
                                FilterChip(
                                    selected = state.scanSpeed == speed,
                                    onClick = { onSelectScanSpeed(speed) },
                                    label = { Text(speed.label, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                        Text(
                            "Makin kiri = lebih teliti: lebih banyak port & lambat · makin kanan = cepat tapi port lebih sedikit & bisa ke-skip",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(6.dp)) }

            // ─── Scan Buttons ───
            item { ScanButtonRow(isScanning = state.isScanning, onScan = onScan) }
            item { Spacer(Modifier.height(4.dp)) }

            // ─── Pause + Stop + Copy All + Clear ───
            item {
                ActionButtons(
                    isScanning = state.isScanning,
                    onStop = onStop,
                    isPaused = state.isPaused,
                    onPauseResume = onPauseResume,
                    hasResults = state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty(),
                    onCopyAll = onCopyAll,
                    onClear = { showClearConfirm = true }
                )
            }
            item { Spacer(Modifier.height(4.dp)) }

            // ─── Status Bar ───
            item {
                StatusBar(
                    text = state.summary,
                    isOk = state.isSummaryOk,
                    isScanning = state.isScanning,
                    progress = state.progress,
                    progressPercent = state.progressPercent
                )
            }

            if (state.copyFeedback != null) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(state.copyFeedback, fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.hostSummary.isNotBlank()) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(state.hostSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ─── Diff antar scan ───
            if (!state.isScanning && state.diff != null) {
                val d = state.diff!!
                if (d.added.isNotEmpty() || d.removed.isNotEmpty() || d.changed.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { showDiffDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CompareArrows, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Perubahan: +${d.added.size} baru · -${d.removed.size} hilang · ~${d.changed.size} berubah",
                                fontSize = 11.sp)
                        }
                    }
                }
            }

            // ─── Sort ───
            if (state.hosts.isNotEmpty() && onSortMode != null) {
                item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        SortBar(currentSort = state.sortMode, onSortMode = onSortMode)
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }

            // ─── Monitor ───
            if (state.scanType == ScanType.MONITOR && state.monitor.isRunning) {
                item { MonitorDisplay(state.monitor) }
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (filteredHosts.size == state.hosts.size) "Hosts (${state.hosts.size})"
                                else "Hosts (${filteredHosts.size}/${state.hosts.size})",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showMapDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("🗺 Peta", fontSize = 11.sp) }
                            TextButton(
                                onClick = { showMatrixDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("▦ Matriks", fontSize = 11.sp) }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onSearchChange?.invoke(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari IP / hostname / MAC", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onSearchChange?.invoke("") }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                    }
                                }
                            } else null,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DeviceFilter.entries.forEach { f ->
                                FilterChip(
                                    selected = state.deviceFilter == f,
                                    onClick = { onDeviceFilter?.invoke(f) },
                                    label = { Text(f.label, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }

                if (filteredHosts.isEmpty()) {
                    item {
                        Text("Tidak ada host yang cocok", color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    items(filteredHosts, key = { it.ip }) { host ->
                        HostCard(
                            host = host,
                            onCopyIp = onCopyIp,
                            onWol = onWol,
                            isFavorite = host.ip in state.favoriteIps,
                            onToggleFavorite = { onToggleFavorite?.invoke(host.ip) },
                            onShowDetail = { detailHost = host },
                            isSelected = host.ip in state.selectedHosts,
                            selectionMode = state.selectedHosts.isNotEmpty(),
                            onToggleSelect = { onToggleHostSelect?.invoke(host.ip) },
                            onRescanHost = if (state.isScanning || onRescanHost == null) null
                            else ({ onRescanHost(host.ip) })
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
                            Text("No results found", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("NetRadar v${BuildConfig.VERSION_NAME}  |  Julius Rudi Tasirin",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoBar(
    localIp: String, gateway: String, subnet: String,
    interfaces: List<NetworkInterfaceInfo>, selectedInterface: String,
    onSelectInterface: ((String) -> Unit)?
) {
    var showInterfacePicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetworkChip(icon = Icons.Default.NetworkCell, text = localIp)
            if (gateway.isNotEmpty()) NetworkChip(icon = Icons.Default.SettingsEthernet, text = gateway)
            if (subnet.isNotEmpty()) NetworkChip(icon = Icons.Default.Cloud, text = subnet)
        }
        if (interfaces.size > 1) {
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = { showInterfacePicker = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("Interface: ${selectedInterface.ifBlank { "Auto" }}", fontSize = 10.sp)
            }
        }
    }

    if (showInterfacePicker) {
        AlertDialog(
            onDismissRequest = { showInterfacePicker = false },
            title = { Text("Select Interface", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    interfaces.forEach { ni ->
                        TextButton(
                            onClick = {
                                onSelectInterface?.invoke(ni.name)
                                showInterfacePicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${ni.name} — ${ni.ip}${if (ni.isActive) " (active)" else ""}",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInterfacePicker = false }) { Text("Close") } }
        )
    }
}

@Composable
fun NetworkChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(icon, null, Modifier.size(12.dp), tint = AccentGreen)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NetRadar v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("About", fontSize = 11.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ports", fontSize = 11.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Help", fontSize = 11.sp) })
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    0 -> {
                        Text("Network Radar Scanner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Created by Julius Rudi Tasirin", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Package: com.tasirin.network.radar", fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Build: Kotlin + Jetpack Compose", fontSize = 10.sp, color = TextSecondary)
                    }
                    1 -> {
                        Text("Common Ports (${PortRangeParser.defaultPorts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        val portList = PortRangeParser.defaultPorts.toList().chunked(10)
                        portList.forEach { row ->
                            Text(row.joinToString(", "), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            Spacer(Modifier.height(2.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Level rendah (kiri) → makin banyak port discan", fontSize = 11.sp, color = AccentGreen)
                    }
                    2 -> {
                        Text("How to Use:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        HelpBullet("Enter IP → scans full /24 subnet")
                        HelpBullet("Enter domain → resolves to IP then scans /24")
                        HelpBullet("Enter CIDR → scans that subnet (e.g. 192.168.1.0/24)")
                        HelpBullet("Enter URL → extracts host and scans /24")
                        Spacer(Modifier.height(6.dp))
                        Text("Tips:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        HelpBullet("Speed level: kiri = teliti + banyak port, kanan = cepat + sedikit port")
                        HelpBullet("Long-press on port chip for port info")
                        HelpBullet("Traceroute shows each hop (needs ping -t)")
                        HelpBullet("Long-press host to select, then delete many at once")
                        HelpBullet("Results persist until you delete them")
                        HelpBullet("Refresh icon re-scans a single host")
                        HelpBullet("NEW badge = device first seen this scan")
                        HelpBullet("WoL button ⚡ wakes sleeping devices")
                        HelpBullet("Monitor mode pings every 1.5s")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun HelpBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(" • ", fontSize = 11.sp, color = TextSecondary)
        Text(text, fontSize = 11.sp, color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBar(currentSort: SortMode, onSortMode: (SortMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Sort, null, Modifier.size(14.dp), tint = TextSecondary)
        Text("Sort:", fontSize = 11.sp, color = TextSecondary)
        SortMode.entries.forEach { mode ->
            FilterChip(
                selected = currentSort == mode,
                onClick = { onSortMode(mode) },
                label = { Text(mode.label, fontSize = 10.sp) },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorDisplay(monitor: MonitorState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, null, Modifier.size(16.dp), tint = AccentGreen)
                Spacer(Modifier.width(6.dp))
                Text("Monitoring ${monitor.statuses.size} perangkat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${monitor.pings} siklus", fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            val online = monitor.statuses.values.count { it }
            Text("$online/${monitor.statuses.size} online",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (online > 0) StatusGreen else StatusRed)
            Spacer(Modifier.height(6.dp))
            if (monitor.statuses.isEmpty()) {
                Text("Waiting...", fontSize = 11.sp, color = TextSecondary)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    monitor.statuses.forEach { (ip, ok) ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (ok) StatusGreen.copy(alpha = 0.18f) else StatusRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                ip,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (ok) StatusGreen else StatusRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostDetailDialog(
    host: HostInfo,
    isFavorite: Boolean,
    uptime: List<UptimeEvent>,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(host.ip, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                host.hostname?.takeIf { it != host.ip }?.let {
                    DetailLine("Hostname", it)
                }
                host.macAddress?.let {
                    DetailLine("MAC", it + (host.macVendor?.let { v -> " ($v)" } ?: ""))
                }
                host.osGuess?.let { DetailLine("OS", it) }
                host.latencyMs?.let { DetailLine("Latency", "${it}ms") }
                DetailLine("Port terbuka", host.openPorts.size.toString())
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null, Modifier.size(16.dp),
                        tint = if (isFavorite) Color(0xFFFFB300) else TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFavorite) "Hapus dari perangkat penting" else "Jadikan perangkat penting", fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Riwayat ketersediaan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                if (uptime.isEmpty()) {
                    Text("Belum ada riwayat", fontSize = 11.sp, color = TextSecondary)
                } else {
                    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                    uptime.takeLast(10).reversed().forEach { e ->
                        Text(
                            "${fmt.format(Date(e.ts))} — ${if (e.online) "online" else "offline"}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (e.online) StatusGreen else StatusRed
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun ScanDiffDialog(diff: ScanDiff, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perubahan antar scan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DiffSection("Baru (+${diff.added.size})", diff.added)
                DiffSection("Hilang (-${diff.removed.size})", diff.removed)
                DiffSection("Berubah (~${diff.changed.size})", diff.changed)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DiffSection(title: String, hosts: List<HostInfo>) {
    if (hosts.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    hosts.forEach { host ->
        val ports = host.openPorts.take(8).joinToString(",") { it.port.toString() }
        Text(
            "${host.ip}${if (ports.isNotEmpty()) " [$ports]" else ""}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkMapDialog(hosts: List<HostInfo>, gateway: String, onDismiss: () -> Unit) {
    val nodes = hosts.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Peta jaringan", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val textMeasurer = rememberTextMeasurer()
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = minOf(size.width, size.height) / 2f - 30.dp.toPx()
                    drawCircle(color = primaryColor, radius = 10.dp.toPx(), center = Offset(cx, cy))
                    val centerLabel = textMeasurer.measure(
                        AnnotatedString(gateway.ifBlank { "Gateway" }),
                        TextStyle(fontSize = 9.sp, color = Color.DarkGray)
                    )
                    drawText(centerLabel, topLeft = Offset(cx - centerLabel.size.width / 2f, cy + 13.dp.toPx()))
                    if (nodes.isEmpty()) return@Canvas
                    nodes.forEachIndexed { i, host ->
                        val angle = 2.0 * PI * i / nodes.size
                        val x = (cx + radius * cos(angle).toFloat()).coerceIn(0f, size.width)
                        val y = (cy + radius * sin(angle).toFloat()).coerceIn(0f, size.height)
                        val nodePos = Offset(x, y)
                        drawLine(
                            start = Offset(cx, cy),
                            end = nodePos,
                            color = hostColor(host).copy(alpha = 0.55f),
                            strokeWidth = (1 + host.openPorts.size.coerceAtMost(4)).dp.toPx()
                        )
                        drawCircle(color = hostColor(host), radius = 5.dp.toPx(), center = nodePos)
                        val label = textMeasurer.measure(
                            AnnotatedString(host.ip.substringAfterLast('.')),
                            TextStyle(fontSize = 8.sp, color = Color.Gray)
                        )
                        drawText(label, topLeft = Offset(x - label.size.width / 2f, y + 6.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("● Camera", fontSize = 10.sp, color = Color(0xFFE53935))
                    Text("● Router", fontSize = 10.sp, color = Color(0xFF1E88E5))
                    Text("● Share", fontSize = 10.sp, color = Color(0xFFFB8C00))
                    Text("● Printer", fontSize = 10.sp, color = Color(0xFF8E24AA))
                    Text("● NAS", fontSize = 10.sp, color = Color(0xFF00ACC1))
                    Text("● TV", fontSize = 10.sp, color = Color(0xFFD81B60))
                    Text("● IoT", fontSize = 10.sp, color = Color(0xFF6D4C41))
                    Text("● HP", fontSize = 10.sp, color = Color(0xFF5E35B1))
                    Text("● Lainnya", fontSize = 10.sp, color = Color(0xFF43A047))
                }
                Text("Pusat = gateway · tebal garis = jumlah port", fontSize = 9.sp, color = TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

private fun hostColor(host: HostInfo): Color = when {
    DeviceKind.CAMERA in host.deviceKinds() -> Color(0xFFE53935)
    DeviceKind.ROUTER in host.deviceKinds() -> Color(0xFF1E88E5)
    DeviceKind.SHARE in host.deviceKinds() -> Color(0xFFFB8C00)
    DeviceKind.PRINTER in host.deviceKinds() -> Color(0xFF8E24AA)
    DeviceKind.NAS in host.deviceKinds() -> Color(0xFF00ACC1)
    DeviceKind.TV in host.deviceKinds() -> Color(0xFFD81B60)
    DeviceKind.IOT in host.deviceKinds() -> Color(0xFF6D4C41)
    DeviceKind.PHONE in host.deviceKinds() -> Color(0xFF5E35B1)
    else -> Color(0xFF43A047)
}

@Composable
private fun PortMatrixDialog(hosts: List<HostInfo>, onDismiss: () -> Unit) {
    val rows = hosts.take(50)
    val ports = PortRangeParser.defaultPorts.take(16)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Matriks port × host", fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("IP", Modifier.width(92.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        ports.forEach { p ->
                            Text("$p", Modifier.width(24.dp), fontSize = 8.sp, color = TextSecondary)
                        }
                    }
                    rows.forEach { host ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(host.ip, Modifier.width(92.dp), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            ports.forEach { p ->
                                val open = host.openPorts.any { it.port == p }
                                Box(
                                    Modifier.padding(1.dp).size(22.dp).background(
                                        if (open) StatusGreen.copy(alpha = 0.75f) else Color(0x1F000000),
                                        RoundedCornerShape(3.dp)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
