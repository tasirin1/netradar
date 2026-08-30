package com.tasirin.network.radar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.BuildConfig
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.AmbientDashboard
import com.tasirin.network.radar.ui.components.DetailLine
import com.tasirin.network.radar.ui.components.HistoryRow
import com.tasirin.network.radar.ui.components.MonitorDisplay
import com.tasirin.network.radar.ui.components.SectionHeader
import com.tasirin.network.radar.ui.components.SettingsBody
import com.tasirin.network.radar.ui.theme.StatusGreen
import com.tasirin.network.radar.ui.theme.StatusOrange
import com.tasirin.network.radar.ui.theme.StatusRed
import com.tasirin.network.radar.ui.theme.TextSecondary

/** Halaman Monitor: status jaringan, pemantauan berjalan, dan ketersediaan host hari ini. */
@Composable
fun MonitorPage(
    monitor: MonitorState,
    hosts: List<HostInfo>,
    uptime: Map<String, List<UptimeEvent>>,
    networkInfo: NetworkInfo,
    gatewayOnline: Boolean?,
    gatewayLatencyMs: Long?,
    internetOnline: Boolean?,
    internetLatencyMs: Long?,
    networkQualityLabel: String
) {
    var showDashboard by remember { mutableStateOf(false) }
    if (showDashboard) {
        AmbientDashboard(monitor = monitor, gateway = networkInfo.gateway, onDismiss = { showDashboard = false })
    }

    val rows = remember(hosts, uptime) {
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        hosts.map { host ->
            val day = uptime[host.ip].orEmpty().filter { it.ts >= dayAgo }
            val pct = if (day.isEmpty()) null else day.count { it.online } * 100 / day.size
            host to pct
        }.sortedWith(compareByDescending<Pair<HostInfo, Int?>> { it.second ?: -1 }.thenBy { it.first.ip })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Status Jaringan") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (networkInfo.localIp.isNotEmpty()) DetailLine("IP lokal", networkInfo.localIp)
                    if (networkInfo.gateway.isNotEmpty()) {
                        val gw = when {
                            gatewayOnline == false -> "✗"
                            gatewayLatencyMs != null -> "${gatewayLatencyMs}ms"
                            else -> "cek…"
                        }
                        DetailLine("Gateway", "${networkInfo.gateway} ($gw)")
                    }
                    if (networkInfo.subnet.isNotEmpty()) DetailLine("Subnet", networkInfo.subnet)
                    val inet = when {
                        internetOnline == false -> "✗"
                        internetLatencyMs != null -> "${internetLatencyMs}ms"
                        else -> "cek…"
                    }
                    DetailLine("Internet", "1.1.1.1 ($inet)")
                    DetailLine("Kualitas", networkQualityLabel.ifBlank { "Mengukur…" })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)); SectionHeader("Pemantauan") }
        item {
            if (monitor.isRunning) {
                MonitorDisplay(monitor = monitor, onDashboard = { showDashboard = true })
            } else {
                Text(
                    "Monitor belum berjalan — mulai dari tab Hasil (tombol Monitor) saat ada host terdeteksi.",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)); SectionHeader("Ketersediaan hari ini") }
        if (rows.isEmpty()) {
            item {
                Text("Belum ada host terdeteksi.", fontSize = 12.sp, color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(rows, key = { it.first.ip }) { (host, pct) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(host.label ?: host.hostname?.takeIf { it != host.ip } ?: host.ip,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(host.ip, fontSize = 10.sp, color = TextSecondary)
                    }
                    if (pct != null) {
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (pct >= 80) StatusGreen else if (pct >= 50) StatusOrange else StatusRed)
                    } else {
                        Text("—", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/** Halaman Riwayat: daftar scan yang sudah selesai. */
@Composable
fun HistoryPage(history: List<ScanHistoryEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Riwayat Scan") }
        if (history.isEmpty()) {
            item {
                Text("Belum ada riwayat scan — hasil scan tersimpan otomatis di sini.",
                    fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(history, key = { it.time }) { entry ->
                HistoryRow(entry)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
    }
}

/** Halaman Pengaturan: tampilan, notifikasi, dan info aplikasi. */
@Composable
fun SettingsPage(
    themeMode: ThemeMode,
    customPorts: String,
    notifyNewDevices: Boolean,
    notifyImportantOffline: Boolean,
    notifyScanDone: Boolean,
    keepScreenOn: Boolean,
    soundEnabled: Boolean,
    autoDiffDialog: Boolean,
    compactMode: Boolean,
    monitorFavoritesOnly: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onCustomPorts: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Unit,
    onNotifyNewDevices: (Boolean) -> Unit,
    onNotifyImportantOffline: (Boolean) -> Unit,
    onNotifyScanDone: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onSoundEnabled: (Boolean) -> Unit,
    onAutoDiffDialog: (Boolean) -> Unit,
    onCompactMode: (Boolean) -> Unit,
    onMonitorFavoritesOnly: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        SectionHeader("Pengaturan")
        Spacer(Modifier.height(4.dp))
        SettingsBody(
            themeMode = themeMode,
            customPorts = customPorts,
            notifyNewDevices = notifyNewDevices,
            notifyImportantOffline = notifyImportantOffline,
            notifyScanDone = notifyScanDone,
            keepScreenOn = keepScreenOn,
            soundEnabled = soundEnabled,
            autoDiffDialog = autoDiffDialog,
            compactMode = compactMode,
            monitorFavoritesOnly = monitorFavoritesOnly,
            onThemeMode = onThemeMode,
            onCustomPorts = onCustomPorts,
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup,
            onNotifyNewDevices = onNotifyNewDevices,
            onNotifyImportantOffline = onNotifyImportantOffline,
            onNotifyScanDone = onNotifyScanDone,
            onKeepScreenOn = onKeepScreenOn,
            onSoundEnabled = onSoundEnabled,
            onAutoDiffDialog = onAutoDiffDialog,
            onCompactMode = onCompactMode,
            onMonitorFavoritesOnly = onMonitorFavoritesOnly
        )
        Spacer(Modifier.height(16.dp))
        Text("NetRadar ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            fontSize = 10.sp, color = TextSecondary)
        Text("Pemindai jaringan lokal — kamera, router, port, dan layanan.",
            fontSize = 10.sp, color = TextSecondary)
    }
}
