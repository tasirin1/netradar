package com.tasirin.network.radar.ui.components

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tasirin.network.radar.BuildConfig
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.*
import com.tasirin.network.radar.ui.theme.*
import com.tasirin.network.radar.util.CrashLog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsBody(
    darkTheme: Boolean?,
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
    onTheme: (Boolean?) -> Unit,
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
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                onImportBackup(reader.readText())
            }
        }
    }
    Column {
        Text("Tampilan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
        }
        OutlinedTextField(
            value = customPorts,
            onValueChange = onCustomPorts,
            label = { Text("Port kustom") },
            placeholder = { Text("22, 80, 8000-8010") },
            supportingText = { Text("Kosongkan untuk memakai port umum sesuai sensitivitas.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        SettingsSwitch("Mode ringkas (sembunyikan sparkline & detail)", compactMode, onCompactMode)
        SettingsSwitch("Cegah layar mati saat scan", keepScreenOn, onKeepScreenOn)
        SettingsSwitch("Monitor hanya perangkat favorit (⭐)", monitorFavoritesOnly, onMonitorFavoritesOnly)
        Spacer(Modifier.height(12.dp))
        Text("Notifikasi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SettingsSwitch("Perangkat baru terdeteksi", notifyNewDevices, onNotifyNewDevices)
        SettingsSwitch("Perangkat penting offline", notifyImportantOffline, onNotifyImportantOffline)
        SettingsSwitch("Ringkasan scan selesai (background)", notifyScanDone, onNotifyScanDone)
        SettingsSwitch("Suara saat perangkat ditemukan", soundEnabled, onSoundEnabled)
        SettingsSwitch("Buka dialog perubahan otomatis setelah scan", autoDiffDialog, onAutoDiffDialog)
        Spacer(Modifier.height(12.dp))
        Text("Backup", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                Text("Export", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Impor", fontSize = 11.sp)
            }
        }
    }
}

/** Satu baris entri riwayat scan (dipakai halaman Riwayat). */
@Composable
fun HistoryRow(entry: ScanHistoryEntry) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("${entry.type} — ${entry.target}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val waktu = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(entry.time))
        val durasi = if (entry.durationMs >= 60_000)
            "${entry.durationMs / 60_000}m ${(entry.durationMs % 60_000) / 1000}s"
        else "${entry.durationMs / 1000}s"
        Text("$waktu · ${entry.hostCount} host · ${entry.portCount} port · $durasi",
            fontSize = 10.sp, color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoBar(
    localIp: String, gateway: String, subnet: String,
    interfaces: List<NetworkInterfaceInfo>, selectedInterface: String,
    onSelectInterface: ((String) -> Unit)?,
    gatewayOnline: Boolean? = null,
    gatewayLatencyMs: Long? = null,
    internetOnline: Boolean? = null,
    internetLatencyMs: Long? = null,
    networkQualityLabel: String = "",
    networkQualityColor: Long = 0xFF00695C
) {
    var showInfo by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showInfo = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.NetworkCell, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(localIp, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val statusText = when {
                gatewayOnline == false -> "Gateway ✗"
                internetOnline == false -> "Internet ✗"
                networkQualityLabel.isNotEmpty() -> "● $networkQualityLabel"
                else -> "● Mengukur…"
            }
            val statusColor = when {
                gatewayOnline == false -> StatusRed
                internetOnline == false -> StatusOrange
                else -> Color(networkQualityColor)
            }
            Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.12f)) {
                Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("ketuk untuk info", fontSize = 9.sp, color = TextSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = TextSecondary)
        }
    }

    if (showInfo) {
        NetworkInfoDialog(
            localIp = localIp, gateway = gateway, subnet = subnet,
            gatewayOnline = gatewayOnline, gatewayLatencyMs = gatewayLatencyMs,
            internetOnline = internetOnline, internetLatencyMs = internetLatencyMs,
            networkQualityLabel = networkQualityLabel, networkQualityColor = networkQualityColor,
            interfaces = interfaces, selectedInterface = selectedInterface,
            onSelectInterface = onSelectInterface,
            onDismiss = { showInfo = false }
        )
    }
}

/** Dialog info jaringan lengkap (dibuka dari baris ringkas NetworkInfoBar). */
@Composable
private fun NetworkInfoDialog(
    localIp: String,
    gateway: String,
    subnet: String,
    gatewayOnline: Boolean?,
    gatewayLatencyMs: Long?,
    internetOnline: Boolean?,
    internetLatencyMs: Long?,
    networkQualityLabel: String,
    networkQualityColor: Long,
    interfaces: List<NetworkInterfaceInfo>,
    selectedInterface: String,
    onSelectInterface: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info Jaringan", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailLine("IP lokal", localIp)
                if (gateway.isNotEmpty()) {
                    val gwStatus = when {
                        gatewayOnline == false -> "✗"
                        gatewayLatencyMs != null -> "${gatewayLatencyMs}ms"
                        else -> "cek…"
                    }
                    DetailLine("Gateway", "$gateway ($gwStatus)")
                }
                if (subnet.isNotEmpty()) DetailLine("Subnet", subnet)
                val inetStatus = when {
                    internetOnline == false -> "✗"
                    internetLatencyMs != null -> "${internetLatencyMs}ms"
                    else -> "cek…"
                }
                DetailLine("Internet", "1.1.1.1 ($inetStatus)")
                DetailLine("Kualitas", networkQualityLabel.ifBlank { "Mengukur…" })
                if (interfaces.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text("Interface", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Aktif: ${selectedInterface.ifBlank { "Auto" }}", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    interfaces.forEach { ni ->
                        TextButton(
                            onClick = {
                                onSelectInterface?.invoke(ni.name)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${ni.name} — ${ni.ip}${if (ni.isActive) " (aktif)" else ""}",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Label seksi + garis pemisah untuk mengelompokkan area layar. */
@Composable
fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.width(10.dp))
        Divider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Ringkasan jaringan realtime di header: total host, online, baru, port, URL. */
@Composable
fun NetworkSummaryBar(
    hostCount: Int,
    onlineCount: Int,
    newCount: Int,
    portCount: Int,
    conflictCount: Int,
    urlCount: Int
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("Hosts: $hostCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Online: $onlineCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (onlineCount > 0) StatusGreen else StatusRed
                )
                Spacer(Modifier.weight(1f))
                Text(if (expanded) "sembunyikan detail" else "detail", fontSize = 9.sp, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, Modifier.size(16.dp), tint = TextSecondary
                )
            }
            if (expanded) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                ) {
                    if (newCount > 0) SummaryChip("Baru: $newCount", AccentGreen)
                    if (portCount > 0) SummaryChip("Port: $portCount", StatusBlue)
                    if (conflictCount > 0) SummaryChip("⚠ Konflik: $conflictCount", StatusOrange)
                    if (urlCount > 0) SummaryChip("URL: $urlCount", TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Dialog pengaturan: tema, notifikasi, dan perilaku (tersimpan otomatis). */
@Composable
fun SettingsSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Dialog pilihan visualisasi (Peta / Matriks / Rasi bintang). */
@Composable
fun VisualizationDialog(
    onMap: () -> Unit,
    onMatrix: () -> Unit,
    onConstellation: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visualisasi", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                VisualOption("🗺 Peta jaringan", "Posisi perangkat relatif terhadap gateway", onMap)
                Spacer(Modifier.height(6.dp))
                VisualOption("▦ Matriks port", "Rangkuman port per perangkat", onMatrix)
                Spacer(Modifier.height(6.dp))
                VisualOption("✦ Rasi bintang", "Perangkat sebagai bintang, bisa dibagikan", onConstellation)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun VisualOption(title: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

/** Dialog filter host: jenis perangkat + status (dipindah dari halaman utama). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostFilterDialog(
    deviceFilter: DeviceFilter,
    onDeviceFilter: (DeviceFilter) -> Unit,
    statusFilter: HostStatusFilter,
    onStatusFilter: (HostStatusFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Jenis perangkat", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(3.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeviceFilter.entries.forEach { f ->
                        FilterChip(
                            selected = deviceFilter == f,
                            onClick = { onDeviceFilter(f) },
                            label = { Text(f.label, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Status", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(3.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HostStatusFilter.entries.forEach { f ->
                        FilterChip(
                            selected = statusFilter == f,
                            onClick = { onStatusFilter(f) },
                            label = { Text(f.label, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Dialog pilihan sensitivitas: angka detail & penjelasan ditampilkan di sini. */
@Composable
fun ScanSpeedDialog(
    current: ScanSpeed,
    onSelect: (ScanSpeed) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sensitivitas Scan", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ScanSpeed.entries.forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(s); onDismiss() }
                            .padding(vertical = 6.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (s == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                s.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (s == current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.hostLocal} host paralel · timeout ${s.timeoutMs}ms · ${s.portCount} port umum",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        if (s == current) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kiri = lebih teliti (banyak port, lambat) · Kanan = lebih cepat. " +
                        "Makin rendah level, makin banyak port umum yang discan dan makin kecil risiko skip.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Dialog daftar riwayat scan (target, waktu, hasil). */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Kalau ada jejak crash, buka langsung di tab Crash biar mudah dilaporkan
    var tab by remember { mutableStateOf(if (CrashLog.read(context) != null) 3 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NetRadar v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("About", fontSize = 11.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ports", fontSize = 11.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Help", fontSize = 11.sp) })
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Crash", fontSize = 11.sp) })
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
                        HelpBullet("Enter IP awal → lanjut otomatis (mis. 192.168.15.1 → sampai 192.168.255.x)")
                        HelpBullet("Enter domain → resolves to IP then scans /24")
                        HelpBullet("Enter CIDR → scans that subnet (e.g. 192.168.1.0/24)")
                        HelpBullet("Enter rentang → mis. 192.168.15.1-192.168.16.1 (lintas subnet)")
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
                    3 -> {
                        var crashLog by remember { mutableStateOf(CrashLog.read(context)) }
                        val log = crashLog
                        if (log == null) {
                            Text("Tidak ada crash tercatat", fontSize = 12.sp, color = TextSecondary)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(log, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", log))
                                        } catch (_: Exception) { }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Salin", fontSize = 11.sp) }
                                TextButton(
                                    onClick = {
                                        CrashLog.clear(context)
                                        crashLog = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Hapus", fontSize = 11.sp) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Jika aplikasi force close, salin isi tab ini dan kirim ke pengembang.",
                                fontSize = 10.sp, color = TextSecondary)
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorDisplay(monitor: MonitorState, onDashboard: (() -> Unit)? = null) {
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
            if (onDashboard != null) {
                TextButton(
                    onClick = onDashboard,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("🖥 Mode Dashboard", fontSize = 11.sp)
                }
            }
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

/** Dashboard ambient ukuran penuh: angka besar + denyut status, kebaca dari jauh. */
@Composable
fun AmbientDashboard(monitor: MonitorState, gateway: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val online = monitor.statuses.values.count { it }
                Text("$online/${monitor.statuses.size}",
                    color = if (online > 0) StatusGreen else StatusRed,
                    fontSize = 110.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("perangkat online", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
                val pulse = rememberInfiniteTransition(label = "pulse")
                val alpha by pulse.animateFloat(
                    initialValue = 1f, targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "alpha"
                )
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (online > 0) StatusGreen else StatusRed.copy(alpha = alpha))
                )
                Spacer(Modifier.height(16.dp))
                Text("Gateway: ${gateway.ifBlank { "-" }}",
                    color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("Siklus ke-${monitor.pings}",
                    color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
                if (monitor.statuses.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        monitor.statuses.entries.sortedBy { it.key }.forEach { (ip, ok) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (ok) StatusGreen.copy(alpha = 0.14f) else StatusRed.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(Modifier.size(9.dp).clip(CircleShape)
                                        .background(if (ok) StatusGreen else StatusRed))
                                    Spacer(Modifier.width(6.dp))
                                    Text(ip, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                TextButton(onClick = onDismiss) {
                    Text("Tutup dashboard", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Rasi bintang jaringan: perangkat sebagai bintang + garis aktivitas, bisa dibagikan sebagai gambar. */
@Composable
fun ConstellationDialog(hosts: List<HostInfo>, gateway: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val nodes = hosts.take(60)
    val bitmap = remember(nodes) { buildConstellationBitmap(nodes, gateway, 900, 640) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rasi bintang jaringan", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Peta rasi bintang jaringan",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { shareConstellationImage(context, bitmap) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bagikan gambar", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Gateway: ${gateway.ifBlank { "-" }}", fontSize = 10.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Gambar peta konstelasi langsung ke Bitmap (background gelap, bintang + garis + label). */
private fun buildConstellationBitmap(hosts: List<HostInfo>, gateway: String, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val bg = android.graphics.Paint().apply { color = android.graphics.Color.rgb(18, 18, 18) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
    if (hosts.isEmpty()) return bitmap

    val cx = width / 2f
    val cy = height / 2f
    val r = minOf(width, height) / 2f - 60f
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(70, 0, 137, 123)
        strokeWidth = 2f
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(144, 164, 174)
        textSize = 24f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val pts = hosts.mapIndexed { i, _ ->
        val t = i.toFloat() / hosts.size
        val angle = t * (PI.toFloat() * 5f)  // spiral 2.5 putaran
        val rad = r * (0.25f + 0.75f * t)
        Offset(cx + rad * cos(angle), cy + rad * sin(angle))
    }
    // garis dari gateway (pusat) ke tiap bintang
    pts.forEach { p -> canvas.drawLine(cx, cy, p.x, p.y, linePaint) }
    // garis antar bintang berurutan
    linePaint.color = android.graphics.Color.argb(60, 0, 137, 123)
    pts.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, linePaint) }
    // gateway di pusat
    val gwPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(0, 150, 136)
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, 16f, gwPaint)
    labelPaint.color = android.graphics.Color.WHITE
    canvas.drawText(gateway.ifBlank { "Gateway" }, cx, cy - 26f, labelPaint)

    hosts.forEachIndexed { i, host ->
        val p = pts[i]
        val starPaint = android.graphics.Paint().apply {
            color = if (host.isAlive) android.graphics.Color.rgb(46, 125, 50)
            else android.graphics.Color.rgb(198, 40, 40)
            isAntiAlias = true
        }
        canvas.drawCircle(p.x, p.y, 9f, starPaint)
        labelPaint.color = android.graphics.Color.rgb(224, 224, 224)
        canvas.drawText(host.ip, p.x, p.y + 32f, labelPaint)
    }
    return bitmap
}

/** Simpan bitmap ke PNG di cache lalu share lewat intent FileProvider. */
private fun shareConstellationImage(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.cacheDir, "constellation.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan peta jaringan"))
    } catch (_: Exception) { }
}

@Composable
fun HostDetailDialog(
    host: HostInfo,
    isFavorite: Boolean,
    uptime: List<UptimeEvent>,
    pingHistory: List<PingEvent> = emptyList(),
    onToggleFavorite: () -> Unit,
    onSetLabel: ((String?) -> Unit)? = null,
    onDeepScan: (() -> Unit)? = null,
    onExpandScan: (() -> Unit)? = null,
    onResolveHostname: (() -> Unit)? = null,
    onCopyIp: (() -> Unit)? = null,
    onWol: (() -> Unit)? = null,
    onRescan: (() -> Unit)? = null,
    onPing: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var labelText by remember(host.ip) { mutableStateOf(host.label ?: "") }
    var tab by remember(host.ip) { mutableStateOf(0) }
    val webUrl = remember(host) {
        val webPort = host.openPorts.firstOrNull { it.port in WEB_PORTS } ?: host.openPorts.firstOrNull()
        if (webPort != null) {
            val scheme = if (webPort.port == 443 || webPort.port == 8443) "https" else "http"
            "$scheme://${host.ip}:${webPort.port}/"
        } else "http://${host.ip}/"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(host.ip, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Info", fontSize = 11.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Port", fontSize = 11.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Riwayat", fontSize = 11.sp) })
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = { labelText = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    placeholder = { Text("Nama perangkat", fontSize = 12.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = { onSetLabel?.invoke(labelText) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) { Text("Simpan", fontSize = 12.sp) }
                            }
                            Spacer(Modifier.height(6.dp))
                            host.hostname?.takeIf { it != host.ip }?.let { DetailLine("Hostname", it) }
                            host.macAddress?.let {
                                DetailLine("MAC", it + (host.macVendor?.let { v -> " ($v)" } ?: ""))
                            }
                            host.osGuess?.let { DetailLine("OS", it) }
                            host.latencyMs?.let { DetailLine("Latency", "${it}ms") }
                            DetailLine("Port terbuka", host.openPorts.size.toString())
                            if (host.ipConflict) {
                                Spacer(Modifier.height(4.dp))
                                Surface(shape = MaterialTheme.shapes.small, color = StatusOrange.copy(alpha = 0.15f)) {
                                    Text("⚠ Kemungkinan konflik IP — MAC berbeda dari deteksi sebelumnya",
                                        fontSize = 11.sp, color = StatusOrange,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { try { uriHandler.openUri(webUrl) } catch (_: Exception) {} },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, null, Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browser", fontSize = 11.sp)
                                }
                                if (onCopyIp != null) {
                                    OutlinedButton(onClick = onCopyIp,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Salin IP", fontSize = 11.sp)
                                    }
                                }
                                if (onPing != null) {
                                    OutlinedButton(onClick = onPing,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.NetworkCheck, null, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ping", fontSize = 11.sp)
                                    }
                                }
                                if (onRescan != null) {
                                    OutlinedButton(onClick = onRescan,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rescan", fontSize = 11.sp)
                                    }
                                }
                                if (onWol != null) {
                                    OutlinedButton(onClick = onWol,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(12.dp),
                                            tint = AccentGreen)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Wake on LAN", fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onToggleFavorite) {
                                Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    null, Modifier.size(16.dp),
                                    tint = if (isFavorite) Color(0xFFFFB300) else TextSecondary)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isFavorite) "Hapus dari perangkat penting"
                                    else "Jadikan perangkat penting", fontSize = 12.sp)
                            }
                            if (onDeepScan != null) {
                                OutlinedButton(
                                    onClick = onDeepScan,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Deep scan semua port (1–65535)", fontSize = 12.sp)
                                }
                            }
                            if (onExpandScan != null) {
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = onExpandScan,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan subnet /24 sekitar IP ini", fontSize = 12.sp)
                                }
                            }
                            if (onResolveHostname != null) {
                                TextButton(
                                    onClick = onResolveHostname,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(12.dp), tint = TextSecondary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cari nama DNS (reverse lookup)", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                        1 -> {
                            Text("Daftar port (${host.openPorts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            if (host.openPorts.isEmpty()) {
                                Text("Tidak ada port terbuka — coba Rescan atau Deep scan.",
                                    fontSize = 11.sp, color = TextSecondary)
                            } else {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    host.openPorts.take(200).forEach { p ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            tonalElevation = 1.dp
                                        ) {
                                            Text(
                                                "${p.port}${p.service?.let { " $it" } ?: ""}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (host.openPorts.size > 200) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("… +${host.openPorts.size - 200} port lainnya",
                                        fontSize = 9.sp, color = TextSecondary)
                                }
                            }
                        }
                        else -> {
                            Text("Riwayat ketersediaan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            if (uptime.isEmpty()) {
                                Text("Belum ada riwayat", fontSize = 11.sp, color = TextSecondary)
                            } else {
                                UptimeChart(uptime)
                                Spacer(Modifier.height(4.dp))
                                val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000
                                val day = uptime.filter { it.ts >= dayAgo }
                                if (day.isNotEmpty()) {
                                    val onlinePct = day.count { it.online } * 100 / day.size
                                    Text("24 jam terakhir: $onlinePct% online", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                }
                                val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                uptime.takeLast(5).reversed().forEach { e ->
                                    Text(
                                        "${fmt.format(Date(e.ts))} — ${if (e.online) "online" else "offline"}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (e.online) StatusGreen else StatusRed
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Riwayat ping (latency)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            if (pingHistory.isEmpty()) {
                                Text("Belum ada riwayat ping — tekan Ping di tab Info", fontSize = 11.sp, color = TextSecondary)
                            } else {
                                PingChart(pingHistory)
                                Spacer(Modifier.height(4.dp))
                                val last = pingHistory.last()
                                Text("Ping terakhir: ${last.latencyMs}ms · total ${pingHistory.size} pengukuran",
                                    fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Grafik garis latency ping (60 titik terakhir). */
@Composable
private fun PingChart(events: List<PingEvent>) {
    if (events.isEmpty()) return
    val recent = events.takeLast(60)
    val maxLat = recent.maxOf { it.latencyMs }.coerceAtLeast(1)
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val stepX = if (recent.size > 1) size.width / (recent.size - 1) else size.width
        val points = recent.mapIndexed { i, e ->
            Offset(
                x = i * stepX,
                y = size.height - (e.latencyMs.toFloat() / maxLat) * size.height
            )
        }
        points.zipWithNext().forEach { (a, b) ->
            drawLine(color = StatusBlue, start = a, end = b, strokeWidth = 2.dp.toPx())
        }
        points.forEachIndexed { i, p ->
            val lat = recent[i].latencyMs
            val color = when { lat < 10 -> StatusGreen; lat < 50 -> StatusOrange; else -> StatusRed }
            drawCircle(color = color, radius = 2.5.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun UptimeChart(events: List<UptimeEvent>) {
    if (events.isEmpty()) return
    val recent = events.takeLast(60)
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * (recent.size - 1)) / recent.size).coerceAtLeast(1f)
        recent.forEachIndexed { i, e ->
            drawRect(
                color = if (e.online) StatusGreen else StatusRed,
                topLeft = Offset(i * (barW + gap), 0f),
                size = Size(barW, size.height)
            )
        }
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun ScanDiffDialog(diff: ScanDiff, onDismiss: () -> Unit) {
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
fun NetworkMapDialog(
    hosts: List<HostInfo>,
    gateway: String,
    onHostClick: (HostInfo) -> Unit = {},
    onDismiss: () -> Unit
) {
    val nodes = hosts.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Peta jaringan", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val textMeasurer = rememberTextMeasurer()
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .pointerInput(nodes) {
                            detectTapGestures { tap ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val cx = w / 2f
                                val cy = h / 2f
                                val radius = minOf(w, h) / 2f - 30.dp.toPx()
                                val hitR = 24.dp.toPx()
                                val idx = nodes.indices.firstOrNull { i ->
                                    val angle = 2.0 * PI * i / nodes.size
                                    val x = (cx + radius * cos(angle).toFloat()).coerceIn(0f, w)
                                    val y = (cy + radius * sin(angle).toFloat()).coerceIn(0f, h)
                                    val dx = tap.x - x
                                    val dy = tap.y - y
                                    dx * dx + dy * dy <= hitR * hitR
                                } ?: -1
                                if (idx >= 0) onHostClick(nodes[idx])
                            }
                        }
                ) {
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
                Text("Pusat = gateway · tebal garis = jumlah port · ketuk node untuk detail",
                    fontSize = 9.sp, color = TextSecondary)
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
fun PortMatrixDialog(hosts: List<HostInfo>, onDismiss: () -> Unit) {
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
