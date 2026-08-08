package com.tasirin.network.radar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.ui.components.AboutDialog
import com.tasirin.network.radar.ui.theme.*
import com.tasirin.network.radar.viewmodel.ScanUiState

/** Halaman utama NetRadar: navigasi 4 tab (Hasil, Monitor, Riwayat, Pengaturan). */
enum class MainTab(val label: String, val icon: ImageVector) {
    HASIL("Hasil", Icons.Default.Search),
    MONITOR("Monitor", Icons.Default.WifiTethering),
    RIWAYAT("Riwayat", Icons.Default.History),
    PENGATURAN("Pengaturan", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: ScanUiState,
    onTargetChange: (String) -> Unit,
    onScan: (ScanType) -> Unit,
    onStop: () -> Unit,
    onPauseResume: (() -> Unit)? = null,
    onToggleSettings: (() -> Unit)? = null,
    onSetTheme: ((Boolean?) -> Unit)? = null,
    onSetNotifyNewDevices: ((Boolean) -> Unit)? = null,
    onSetNotifyImportantOffline: ((Boolean) -> Unit)? = null,
    onSetNotifyScanDone: ((Boolean) -> Unit)? = null,
    onSetKeepScreenOn: ((Boolean) -> Unit)? = null,
    onSetSoundEnabled: ((Boolean) -> Unit)? = null,
    onSetAutoDiffDialog: ((Boolean) -> Unit)? = null,
    onSetCompactMode: ((Boolean) -> Unit)? = null,
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
    onClearCheckpoint: (() -> Unit)? = null,
    onSetMonitorFavoritesOnly: ((Boolean) -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(MainTab.HASIL) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Tombol kembali (Android) di tab selain Hasil → kembali ke Hasil
    BackHandler(enabled = tab != MainTab.HASIL) { tab = MainTab.HASIL }

    // Kompatibilitas: menu "Pengaturan" lama / state ViewModel membuka tab Pengaturan
    LaunchedEffect(state.showSettings) {
        if (state.showSettings) tab = MainTab.PENGATURAN
    }

    if (state.showAbout) {
        AboutDialog(onDismiss = { onAbout?.invoke() })
    }

    // Konfirmasi hapus semua hasil (dari menu atas)
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
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Salin semua hasil", fontSize = 12.sp) },
                            enabled = state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                            onClick = { menuOpen = false; onCopyAll?.invoke() }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus semua hasil", fontSize = 12.sp) },
                            enabled = state.hosts.isNotEmpty() || state.discoveredUrls.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) },
                            onClick = { menuOpen = false; showClearConfirm = true }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Tentang", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(16.dp)) },
                            onClick = { menuOpen = false; onAbout?.invoke() }
                        )
                        DropdownMenuItem(
                            text = { Text("Pengaturan", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) },
                            onClick = { menuOpen = false; onToggleSettings?.invoke(); tab = MainTab.PENGATURAN }
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
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 10.sp) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.HASIL -> ResultsTab(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onOpenTab = { tab = it },
                    onTargetChange = onTargetChange,
                    onScan = onScan,
                    onStop = onStop,
                    onPauseResume = onPauseResume,
                    onCopyIp = onCopyIp,
                    onToggleHostSelect = onToggleHostSelect,
                    onDeleteSelected = onDeleteSelected,
                    onUndoDelete = onUndoDelete,
                    onSelectAllHosts = onSelectAllHosts,
                    onClearSelection = onClearSelection,
                    onWol = onWol,
                    onSortMode = onSortMode,
                    scanSpeed = scanSpeed,
                    onSelectScanSpeed = onSelectScanSpeed,
                    onSearchChange = onSearchChange,
                    onDeviceFilter = onDeviceFilter,
                    onStatusFilter = onStatusFilter,
                    onRescanHost = onRescanHost,
                    onSelectInterface = onSelectInterface,
                    onToggleFavorite = onToggleFavorite,
                    onDeepScan = onDeepScan,
                    onCancelDeepScan = onCancelDeepScan,
                    onPingHost = onPingHost,
                    onExpandScan = onExpandScan,
                    onResolveHostname = onResolveHostname,
                    onSetHostLabel = onSetHostLabel,
                    onDiffDialogShown = onDiffDialogShown,
                    onConfirmWideScan = onConfirmWideScan,
                    onCancelWideScan = onCancelWideScan,
                    onResumeScan = onResumeScan,
                    onClearCheckpoint = onClearCheckpoint
                )
                MainTab.MONITOR -> MonitorPage(
                    monitor = state.monitor,
                    hosts = state.hosts,
                    uptime = state.uptime,
                    networkInfo = state.networkInfo,
                    gatewayOnline = state.gatewayOnline,
                    gatewayLatencyMs = state.gatewayLatencyMs,
                    internetOnline = state.internetOnline,
                    internetLatencyMs = state.internetLatencyMs,
                    networkQualityLabel = state.networkQualityLabel,
                    networkQualityColor = state.networkQualityColor
                )
                MainTab.RIWAYAT -> HistoryPage(history = state.scanHistory)
                MainTab.PENGATURAN -> SettingsPage(
                    darkTheme = state.isDarkTheme,
                    notifyNewDevices = state.notifyNewDevices,
                    notifyImportantOffline = state.notifyImportantOffline,
                    notifyScanDone = state.notifyScanDone,
                    keepScreenOn = state.keepScreenOn,
                    soundEnabled = state.soundEnabled,
                    autoDiffDialog = state.autoDiffDialog,
                    compactMode = state.compactMode,
                    monitorFavoritesOnly = state.monitorFavoritesOnly,
                    onTheme = { onSetTheme?.invoke(it) },
                    onNotifyNewDevices = { onSetNotifyNewDevices?.invoke(it) },
                    onNotifyImportantOffline = { onSetNotifyImportantOffline?.invoke(it) },
                    onNotifyScanDone = { onSetNotifyScanDone?.invoke(it) },
                    onKeepScreenOn = { onSetKeepScreenOn?.invoke(it) },
                    onSoundEnabled = { onSetSoundEnabled?.invoke(it) },
                    onAutoDiffDialog = { onSetAutoDiffDialog?.invoke(it) },
                    onCompactMode = { onSetCompactMode?.invoke(it) },
                    onMonitorFavoritesOnly = { onSetMonitorFavoritesOnly?.invoke(it) }
                )
            }
        }
    }
}
