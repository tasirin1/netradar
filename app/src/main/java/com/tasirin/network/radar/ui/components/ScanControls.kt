package com.tasirin.network.radar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        label = { Text("Target", fontWeight = FontWeight.Bold) },
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
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
    val buttons = listOf(
        ScanType.PORT_SCAN to "Port Scan",
        ScanType.CAMERA to "CCTV",
        ScanType.ROUTER to "Router",
        ScanType.URL_PATH to "URL Path",
        ScanType.DISCOVER to "Discover",
        ScanType.PING to "Ping Sweep",
        ScanType.UDP to "UDP",
        ScanType.TRACE to "Trace",
        ScanType.MONITOR to "Monitor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // First row: 4 buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buttons.take(4).forEach { (type, label) ->
                Button(
                    onClick = { onScan(type) },
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(text = label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        // Second row: 3 buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            buttons.drop(4).forEach { (type, label) ->
                Button(
                    onClick = { onScan(type) },
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(text = label, fontSize = 10.sp, maxLines = 1)
                }
            }
            // Isi slot kosong bila jumlah tombol baris kedua < 4
            if (buttons.size - 4 < 4) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ActionButtons(
    isScanning: Boolean,
    onStop: () -> Unit,
    isPaused: Boolean = false,
    onPauseResume: (() -> Unit)? = null,
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
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(if (isPaused) "Resume" else "Pause", fontSize = 12.sp)
            }
        }
        Button(
            onClick = onStop,
            enabled = isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text("Stop", fontSize = 12.sp)
        }
    }
}
