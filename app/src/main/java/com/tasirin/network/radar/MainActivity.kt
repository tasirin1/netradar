package com.tasirin.network.radar

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tasirin.network.radar.ui.screens.MainScreen
import com.tasirin.network.radar.ui.theme.NetRadarTheme
import com.tasirin.network.radar.viewmodel.ScanViewModel

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ScanViewModel = viewModel()
            val state = viewModel.state.collectAsStateWithLifecycle().value

            // Cegah layar mati saat scanning / monitoring
            LaunchedEffect(state.isScanning, state.monitor.isRunning) {
                if (state.isScanning || state.monitor.isRunning)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            NetRadarTheme(darkThemeOverride = state.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        state = state,
                        onTargetChange = { viewModel.setTarget(it) },
                        onScan = { type ->
                            // Apply custom ports before scanning
                            viewModel.useCustomPortsForScan()
                            viewModel.startScan(type)
                        },
                        onStop = { viewModel.stopScan() },
                        onPauseResume = {
                            if (state.isPaused) viewModel.resumeScan() else viewModel.pauseScan()
                        },
                        onToggleTheme = { viewModel.toggleDarkTheme() },
                        onCopyIp = { ip -> viewModel.copyToClipboard("IP", ip) },
                        onCopyAll = {
                            viewModel.copyToClipboard("Scan Results", viewModel.copyAllText())
                        },
                        onToggleHostSelect = { ip -> viewModel.toggleHostSelection(ip) },
                        onDeleteSelected = { viewModel.deleteSelectedHosts() },
                        onSelectAllHosts = { viewModel.selectAllHosts() },
                        onClearSelection = { viewModel.clearSelection() },
                        onClearResults = { viewModel.clearResults() },
                        onWol = { ip, mac -> viewModel.wakeOnLan(ip, mac) },
                        onSortMode = { mode -> viewModel.setSortMode(mode) },
                        onAbout = { viewModel.toggleAbout() },
                        onCustomPorts = { ports -> viewModel.setCustomPorts(ports) },
                        onToggleCustomPorts = { viewModel.toggleCustomPorts() },
                        selectedProfile = state.selectedProfile,
                        onSelectProfile = { profile -> viewModel.setPortProfile(profile) },
                        onSelectInterface = { name -> viewModel.selectInterface(name) }
                    )
                }
            }
        }
    }
}
