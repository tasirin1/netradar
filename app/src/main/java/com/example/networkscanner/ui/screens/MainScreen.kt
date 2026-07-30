package com.tasirin.network.radar.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.*
import com.tasirin.network.radar.ui.theme.*
import com.tasirin.network.radar.viewmodel.ScanUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: ScanUiState,
    onTargetChange: (String) -> Unit,
    onScan: (ScanType) -> Unit,
    onStop: () -> Unit,
    onToggleTheme: () -> Unit,
    onCopyIp: ((String) -> Unit)? = null,
    onCopyAll: (() -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    onSortMode: ((SortMode) -> Unit)? = null,
    onCustomPortsChange: ((String) -> Unit)? = null,
    onSelectInterface: ((String) -> Unit)? = null,
    onRefreshNetwork: (() -> Unit)? = null,
    onToggleAbout: (() -> Unit)? = null
) {
    // About dialog
    if (state.showAbout) {
        AboutDialog(onDismiss = onToggleAbout)
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
                    IconButton(onClick = { onToggleAbout?.invoke() }) {
                        Icon(Icons.Default.Info, null, contentDescription = "About")
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // ─── Network Info + Interface Picker ───
            NetworkInfoSection(
                networkInfo = state.networkInfo,
                interfaces = state.networkInterfaces,
                selectedInterface = state.selectedInterface,
                onSelectInterface = onSelectInterface,
                onRefresh = onRefreshNetwork
            )
            Spacer(Modifier.height(6.dp))

            // ─── Target Input ───
            TargetInput(
                value = state.target,
                onValueChange = onTargetChange,
                hint = "Target IP, URL, or CIDR"
            )

            Spacer(Modifier.height(4.dp))

            // ─── Custom Ports Input ───
            if (onCustomPortsChange != null) {
                CustomPortsInput(
                    value = state.customPorts,
                    onValueChange = onCustomPortsChange
                )
                Spacer(Modifier.height(4.dp))
            }

            // ─── Scan Buttons ───
            ScanButtonRow(
                isScanning = state.isScanning,
                onScan = onScan
            )

            Spacer(Modifier.height(4.dp))

            // ─── Stop + Copy All ───
            ActionButtons(
                isScanning = state.isScanning,
                onStop = onStop,
                hasResults = state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty(),
                onCopyAll = onCopyAll
            )

            Spacer(Modifier.height(4.dp))

            // ─── Status Bar ───
            StatusBar(
                text = state.summary,
                isOk = state.isSummaryOk,
                isScanning = state.isScanning,
                progress = state.progress,
                progressPercent = state.progressPercent
            )

            // ─── Copy feedback ───
            if (state.copyFeedback != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = AccentGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = state.copyFeedback,
                        fontSize = 11.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ─── Host Summary ───
            if (state.hostSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.hostSummary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // ─── Sort Controls ───
            if (state.hosts.isNotEmpty() && onSortMode != null) {
                Spacer(Modifier.height(4.dp))
                SortBar(currentSort = state.sortMode, onSortMode = onSortMode)
            }

            Spacer(Modifier.height(6.dp))

            // ─── Monitor display ───
            if (state.scanType == ScanType.MONITOR && state.monitor.isRunning) {
                MonitorDisplay(state.monitor)
                Spacer(Modifier.height(6.dp))
            }

            // ─── Results - Hosts ───
            if (state.hosts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Hosts (${state.hosts.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                HostResultsList(hosts = state.hosts, onCopyIp = onCopyIp, onWol = onWol)
            }

            // ─── Results - URLs ───
            if (state.discoveredUrls.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("URLs (${state.discoveredUrls.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                UrlResultsList(urls = state.discoveredUrls)
            }

            // ─── No results ───
            if (state.hosts.isEmpty() && state.discoveredUrls.isEmpty() && !state.isScanning && state.scanResult != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("No results found", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            Text("NetRadar v2.0  |  Julius Rudi Tasirin",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun NetworkInfoSection(
    networkInfo: NetworkInfo,
    interfaces: List<String>,
    selectedInterface: String,
    onSelectInterface: ((String) -> Unit)?,
    onRefresh: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NetworkCell, null, Modifier.size(14.dp), tint = AccentGreen)
                Spacer(Modifier.width(4.dp))
                Text("Network", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(12.dp), tint = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (networkInfo.localIp.isNotEmpty()) {
                    NetworkChip(Icons.Default.Wifi, networkInfo.localIp)
                }
                if (networkInfo.gateway.isNotEmpty()) {
                    NetworkChip(Icons.Default.SettingsEthernet, "GW: ${networkInfo.gateway}")
                }
                if (networkInfo.subnet.isNotEmpty()) {
                    NetworkChip(Icons.Default.Cloud, networkInfo.subnet)
                }
            }
            // Interface picker
            if (interfaces.size > 1 && onSelectInterface != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsInputAntenna, null, Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Iface:", fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    interfaces.forEach { name ->
                        FilterChip(
                            selected = name == selectedInterface,
                            onClick = { onSelectInterface(name) },
                            label = { Text(name, fontSize = 9.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = AccentGreen)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
        }
    }
}

@Composable
fun CustomPortsInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Custom Ports", fontSize = 12.sp) },
        placeholder = { Text("e.g. 80,443,8080 or 1-1000", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        supportingText = { Text("Leave empty for default 50 ports", fontSize = 9.sp, color = TextSecondary) }
    )
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

@Composable
fun MonitorDisplay(monitor: PingMonitorState) {
    val dotAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(AccentGreen.copy(alpha = dotAlpha))
                )
                Spacer(Modifier.width(6.dp))
                Text("Monitoring ${monitor.ip}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${monitor.history.size} pings", fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val recent = monitor.history.takeLast(20)
                recent.forEach { result ->
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (result.isAlive) StatusGreen else StatusRed
                    ) {}
                }
                if (recent.isEmpty()) Text("Waiting...", fontSize = 11.sp, color = TextSecondary)
            }
            if (monitor.lastLatency != null) {
                Spacer(Modifier.height(4.dp))
                Text("Last: ${monitor.lastLatency}ms", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("NetRadar v2.0", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Network Radar Scanner", fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))

                Text("Cara Pakai:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("• Masukkan IP, domain, atau URL target", fontSize = 12.sp)
                Text("• IP tunggal otomatis diperluas ke /24 subnet", fontSize = 12.sp)
                Text("• Domain otomatis di-resolve ke IP", fontSize = 12.sp)
                Text("• Port Scan: 50 port umum secara paralel", fontSize = 12.sp)
                Text("• Port custom: format 80,443,8080 atau 1-1000", fontSize = 12.sp)

                Spacer(Modifier.height(12.dp))
                Text("Mode Scan:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("📋 Port Scan - 50 port umum", fontSize = 12.sp)
                Text("📷 CCTV - Port kamera (Hikvision, Dahua, dll)", fontSize = 12.sp)
                Text("🌐 Router - Brand & model router", fontSize = 12.sp)
                Text("🔗 URL Path - 200+ path web sensitif", fontSize = 12.sp)
                Text("🔎 Discover - Camera + Router + Shares", fontSize = 12.sp)
                Text("📡 Ping Sweep - ICMP ping scan", fontSize = 12.sp)
                Text("📊 Monitor - Ping kontinu setiap 1.5 detik", fontSize = 12.sp)

                Spacer(Modifier.height(12.dp))
                Text("Port Info:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Tap tahan pada port untuk info detail", fontSize = 12.sp)

                Spacer(Modifier.height(12.dp))
                Text("Package: com.tasirin.network.radar", fontSize = 10.sp, color = TextSecondary)
                Text("© 2026 Julius Rudi Tasirin", fontSize = 10.sp, color = TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss?.invoke() }) { Text("Close") } }
    )
}
