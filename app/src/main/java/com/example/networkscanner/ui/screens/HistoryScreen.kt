package com.example.networkscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import com.example.networkscanner.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryPanel(
    entries: List<HistoryEntry>,
    onLoadEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Icon(Icons.Default.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "History",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClearHistory, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "No scan history yet",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                entries.take(30).forEach { entry ->
                    HistoryItem(entry = entry, onClick = { onLoadEntry(entry.id) })
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            val icon = when (entry.type) {
                "CAMERA" -> Icons.Default.Videocam
                "ROUTER" -> Icons.Default.Router
                "URL_PATH" -> Icons.Default.Http
                "DISCOVER" -> Icons.Default.Explore
                "PING" -> Icons.Default.NetworkCheck
                else -> Icons.Default.Search
            }
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.target,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${entry.type} · ${dateFormat.format(Date(entry.timestamp))}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
