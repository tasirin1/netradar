package com.tasirin.network.radar.ui.screens

import androidx.compose.animation.*
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
    onPauseResume: (() -> Unit)? = null,
    onToggleTheme: () -> Unit,
    onCopyIp: ((String) -> Unit)? = null,
    onCopyAll: (() -> Unit)? = null,
    onDeleteHost: ((String) -> Unit)? = null,
    onClearResults: (() -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    onSortMode: ((SortMode) -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    onCustomPorts: ((String) -> Unit)? = null,
    onToggleCustomPorts: (() -> Unit)? = null,
    selectedProfile: PortProfile = PortProfile.DEFAULT,
    onSelectProfile: ((PortProfile) -> Unit)? = null,
    onSelectInterface: ((String) -> Unit)? = null
) {
    // About dialog
    if (state.showAbout) {
        AboutDialog(onDismiss = { onAbout?.invoke() })
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
                    subnet = state.networkInfo.subnet,
                    interfaces = state.networkInfo.availableInterfaces,
                    selectedInterface = state.networkInfo.selectedInterface,
                    onSelectInterface = onSelectInterface
                )
                Spacer(Modifier.height(6.dp))
            }

            // ─── Target Input ───
            TargetInput(
                value = state.target,
                onValueChange = onTargetChange,
                hint = "Target IP, URL, or CIDR"
            )

            // ─── Custom Ports Toggle ───
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onToggleCustomPorts?.invoke() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        if (state.showCustomPorts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Custom Ports", fontSize = 11.sp)
                }
            }

            // Preset Port Profile
            if (onSelectProfile != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PortProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = state.selectedProfile == profile,
                            onClick = { onSelectProfile(profile) },
                            label = { Text(profile.label, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.showCustomPorts) {
                OutlinedTextField(
                    value = state.customPorts,
                    onValueChange = { onCustomPorts?.invoke(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("80,443,8080 or 1-1000 or 22,80,443,3000-4000", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(Modifier.height(6.dp))

            // ─── Scan Buttons ───
            ScanButtonRow(isScanning = state.isScanning, onScan = onScan)
            Spacer(Modifier.height(4.dp))

            // ─── Pause + Stop + Copy All ───
            ActionButtons(
                isScanning = state.isScanning,
                onStop = onStop,
                isPaused = state.isPaused,
                onPauseResume = onPauseResume,
                hasResults = state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty(),
                onCopyAll = onCopyAll,
                onClear = onClearResults
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

            if (state.copyFeedback != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.copyFeedback, fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
            }

            if (state.hostSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.hostSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            // ─── Sort ───
            if (state.hosts.isNotEmpty() && onSortMode != null) {
                Spacer(Modifier.height(4.dp))
                SortBar(currentSort = state.sortMode, onSortMode = onSortMode)
            }
            Spacer(Modifier.height(6.dp))

            // ─── Monitor ───
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
                HostResultsList(hosts = state.hosts, onCopyIp = onCopyIp, onWol = onWol,
                    onDeleteHost = onDeleteHost)
            }

            // ─── Results - URLs ───
            if (state.discoveredUrls.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("URLs (${state.discoveredUrls.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                UrlResultsList(urls = state.discoveredUrls)
            }

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
            Surface(
                onClick = { showInterfacePicker = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Icon(Icons.Default.SettingsEthernet, null, Modifier.size(12.dp), tint = AccentGreen)
                    Spacer(Modifier.width(4.dp))
                    Text(selectedInterface.ifEmpty { interfaces.firstOrNull()?.name ?: "Auto" },
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(12.dp), tint = TextSecondary)
                }
            }
        }
    }

    if (showInterfacePicker) {
        AlertDialog(
            onDismissRequest = { showInterfacePicker = false },
            title = { Text("Network Interface", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    interfaces.forEach { iface ->
                        Surface(
                            onClick = {
                                onSelectInterface?.invoke(iface.name)
                                showInterfacePicker = false
                            },
                            color = if (iface.name == (selectedInterface.ifEmpty { interfaces.firstOrNull()?.name }))
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                Icon(Icons.Default.SettingsEthernet, null, Modifier.size(16.dp), tint = AccentGreen)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(iface.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(iface.ip, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                                if (iface.isActive) {
                                    Spacer(Modifier.weight(1f))
                                    Text("ACTIVE", fontSize = 9.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInterfacePicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun NetworkChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = AccentGreen)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("NetRadar", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("About", fontSize = 12.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ports", fontSize = 12.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Help", fontSize = 12.sp) })
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    0 -> {
                        Text("Network Radar Scanner", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Version 2.0", fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Created by Julius Rudi Tasirin", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Package: com.tasirin.network.radar", fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Build: Kotlin + Jetpack Compose", fontSize = 10.sp, color = TextSecondary)
                    }
                    1 -> {
                        Text("Default Ports (50)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        val portList = PortRangeParser.defaultPorts.toList().chunked(10)
                        portList.forEach { row ->
                            Text(row.joinToString(", "), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            Spacer(Modifier.height(2.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Custom: 80,443 or 1-1000 or 22,80,3000-4000", fontSize = 11.sp, color = AccentGreen)
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
                        HelpBullet("Preset ports: Web / CCTV / IoT / DB")
                        HelpBullet("Use Custom Ports for specific ports")
                        HelpBullet("Long-press on port chip for port info")
                        HelpBullet("Traceroute shows each hop (needs ping -t)")
                        HelpBullet("Long-press a host card to delete it")
                        HelpBullet("Results persist until you delete them")
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

@Composable
fun MonitorDisplay(monitor: PingMonitorState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
