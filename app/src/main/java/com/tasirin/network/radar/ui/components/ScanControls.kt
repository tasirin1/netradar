package com.tasirin.network.radar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.model.ScanType

@Composable
fun TargetInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun ScanButtonRow(
    isScanning: Boolean,
    onScan: (ScanType) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = listOf(
        ScanType.PORT_SCAN to "Port Scan",
        ScanType.DISCOVER to "Discover",
        ScanType.MONITOR to "Monitor"
    )
    val more = listOf(
        ScanType.CAMERA to "CCTV",
        ScanType.ROUTER to "Router",
        ScanType.URL_PATH to "URL Path",
        ScanType.PING to "Ping Sweep",
        ScanType.UDP to "UDP",
        ScanType.TRACE to "Trace"
    )
    var moreOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        primary.forEach { (type, label) ->
            Button(
                onClick = { onScan(type) },
                enabled = !isScanning,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(label, fontSize = 10.sp, maxLines = 1)
            }
        }
        Box {
            OutlinedButton(
                onClick = { moreOpen = true },
                enabled = !isScanning,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text("Lainnya ▾", fontSize = 10.sp, maxLines = 1)
            }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                more.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 12.sp) },
                        onClick = { moreOpen = false; onScan(type) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScanActionRow(
    isScanning: Boolean,
    onStop: () -> Unit,
    isPaused: Boolean = false,
    onPauseResume: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isScanning && onPauseResume != null) {
            Button(
                onClick = onPauseResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp)
            }
        }
        Button(
            onClick = onStop,
            enabled = isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            Text("Stop", fontSize = 11.sp)
        }
        if (onHistory != null) {
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.History, null, Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Riwayat", fontSize = 11.sp)
            }
        }
    }
}
