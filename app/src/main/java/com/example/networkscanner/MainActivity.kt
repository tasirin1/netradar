package com.example.networkscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.networkscanner.ui.screens.MainScreen
import com.example.networkscanner.ui.theme.NetRadarTheme
import com.example.networkscanner.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {

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
                        onScan = { viewModel.startScan(it) },
                        onStop = { viewModel.stopScan() },
                        onExportJson = { viewModel.exportJson() },
                        onExportCsv = { viewModel.exportCsv() },
                        onExportTxt = { viewModel.exportTxt() },
                        onLoadHistory = { viewModel.loadHistoryEntry(it) },
                        onClearHistory = { viewModel.clearHistory() },
                        onToggleTheme = { viewModel.toggleDarkTheme() }
                    )
                }
            }
        }
    }
}
