package com.example.networkscanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networkscanner.model.ScanType

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
        ScanType.PING to "Ping Sweep"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        buttons.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { (type, label) ->
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
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                // Fill empty slots to keep grid alignment
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ActionButtons(
    isScanning: Boolean,
    onStop: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportTxt: () -> Unit,
    hasResults: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = onStop,
            enabled = isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f)
        ) {
            Text("Stop", fontSize = 12.sp)
        }

        if (hasResults) {
            listOf(
                "JSON" to onExportJson,
                "CSV" to onExportCsv,
                "TXT" to onExportTxt
            ).forEach { (label, action) ->
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 10.sp)
                }
            }
        }
    }
}
