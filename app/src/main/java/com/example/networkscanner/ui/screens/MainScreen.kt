package com.example.networkscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networkscanner.db.HistoryEntry
import com.example.networkscanner.model.*
import com.example.networkscanner.ui.components.*
import com.example.networkscanner.ui.theme.TextSecondary
import com.example.networkscanner.viewmodel.ScanUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: ScanUiState,
    onTargetChange: (String) -> Unit,
    onScan: (ScanType) -> Unit,
    onStop: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportTxt: () -> Unit,
    onLoadHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onToggleTheme: () -> Unit
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

            // Target Input
            TargetInput(
                value = state.target,
                onValueChange = onTargetChange,
                hint = "Target IP, URL, or CIDR"
            )

            Spacer(Modifier.height(6.dp))

            // Scan Buttons
            ScanButtonRow(
                isScanning = state.isScanning,
                onScan = onScan
            )

            Spacer(Modifier.height(4.dp))

            // Action Buttons (Stop + Export)
            ActionButtons(
                isScanning = state.isScanning,
                onStop = onStop,
                onExportJson = onExportJson,
                onExportCsv = onExportCsv,
                onExportTxt = onExportTxt,
                hasResults = state.scanResult != null
            )

            Spacer(Modifier.height(4.dp))

            // Status Bar
            StatusBar(
                text = state.summary,
                isOk = state.isSummaryOk,
                isScanning = state.isScanning,
                progress = state.progress,
                progressPercent = state.progressPercent
            )

            // Host Summary (cameras/routers/shares)
            if (state.hostSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.hostSummary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(6.dp))

            // Results
            if (state.hosts.isNotEmpty()) {
                Text(
                    text = "Hosts (${state.hosts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                HostResultsList(hosts = state.hosts)
            }

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

            if (state.hosts.isEmpty() && state.discoveredUrls.isEmpty() && !state.isScanning && state.scanResult != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff, null,
                            Modifier.size(32.dp), tint = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No results found",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline)

            // History
            Spacer(Modifier.height(6.dp))
            HistoryPanel(
                entries = state.history,
                onLoadEntry = onLoadHistory,
                onClearHistory = onClearHistory
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "NetRadar v2.0  |  Julius Rudi Tasirin",
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
