package com.tasirin.network.radar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
                            val text = viewModel.copyAllText(state.scanResult)
                            viewModel.copyToClipboard("Scan Results", text)
                        },
                        onWol = { ip, mac -> viewModel.wakeOnLan(ip, mac) },
                        onSortMode = { mode -> viewModel.setSortMode(mode) },
                        onAbout = { viewModel.toggleAbout() },
                        onCustomPorts = { ports -> viewModel.setCustomPorts(ports) },
                        onToggleCustomPorts = { viewModel.toggleCustomPorts() },
                        onSelectInterface = { name -> viewModel.selectInterface(name) }
                    )
                }
            }
        }
    }
}
