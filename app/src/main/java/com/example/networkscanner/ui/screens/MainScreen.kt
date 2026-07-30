package com.tasirin.network.radar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSortMode: ((SortMode) -> Unit)? = null
) {
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

            // ─── Network Info ───
            if (state.networkInfo.localIp.isNotEmpty()) {
                NetworkInfoBar(
                    localIp = state.networkInfo.localIp,
                    gateway = state.networkInfo.gateway,
                    subnet = state.networkInfo.subnet
                )
                Spacer(Modifier.height(6.dp))
            }

            // ─── Target Input ───
            TargetInput(
                value = state.target,
                onValueChange = onTargetChange,
                hint = "Target IP, URL, or CIDR"
            )

            Spacer(Modifier.height(6.dp))

            // ─── Scan Buttons ───
            ScanButtonRow(
                isScanning = state.isScanning,
                onScan = onScan
            )

            Spacer(Modifier.height(4.dp))

            // ─── Stop + Copy All + Monitor ───
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
                Text(
                    text = state.copyFeedback,
                    fontSize = 11.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Hosts (${state.hosts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HostResultsList(
                    hosts = state.hosts,
                    onCopyIp = onCopyIp,
                    onWol = onWol
                )
            }

            // ─── Results - URLs ───
            if (state.discoveredUrls.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "URLs (${state.discoveredUrls.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                UrlResultsList(urls = state.discoveredUrls)
            }

            // ─── No results message ───
            if (state.hosts.isEmpty() && state.discoveredUrls.isEmpty() && !state.isScanning && state.scanResult != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("No results found", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }

            // ─── Push to bottom ───
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))

            Text(
                text = "NetRadar v2.0  |  Julius Rudi Tasirin",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun NetworkInfoBar(localIp: String, gateway: String, subnet: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NetworkChip(icon = Icons.Default.NetworkCell, text = localIp)
        if (gateway.isNotEmpty()) NetworkChip(icon = Icons.Default.SettingsEthernet, text = gateway)
        if (subnet.isNotEmpty()) NetworkChip(icon = Icons.Default.Cloud, text = subnet)
    }
}

@Composable
fun NetworkChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SortBar(currentSort: SortMode, onSortMode: (SortMode) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, null, Modifier.size(16.dp), tint = AccentGreen)
                Spacer(Modifier.width(6.dp))
                Text("Monitoring ${monitor.ip}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${monitor.history.size} pings", fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            // Show last 10 results as dots
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val recent = monitor.history.takeLast(20)
                recent.forEach { result ->
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (result.isAlive) StatusGreen else StatusRed
                    ) {}
                }
                if (recent.isEmpty()) {
                    Text("Waiting...", fontSize = 11.sp, color = TextSecondary)
                }
            }
            if (monitor.lastLatency != null) {
                Spacer(Modifier.height(4.dp))
                Text("Last: ${monitor.lastLatency}ms", fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = TextSecondary)
            }
        }
    }
}
