package com.tasirin.network.radar.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.PortDescriptions
import com.tasirin.network.radar.model.PortInfo
import com.tasirin.network.radar.model.UrlDiscovery
import com.tasirin.network.radar.ui.theme.*

@Composable
fun HostResultsList(
    hosts: List<HostInfo>,
    modifier: Modifier = Modifier,
    onCopyIp: ((String) -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    selectedHosts: Set<String> = emptySet(),
    onToggleSelect: ((String) -> Unit)? = null
) {
    if (hosts.isEmpty()) return
    val selectionMode = selectedHosts.isNotEmpty()
    Column(modifier = modifier) {
        hosts.forEach { host ->
            HostCard(
                host = host,
                onCopyIp = onCopyIp,
                onWol = onWol,
                isSelected = host.ip in selectedHosts,
                selectionMode = selectionMode,
                onToggleSelect = onToggleSelect?.let { { it(host.ip) } }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HostCard(
    host: HostInfo,
    onCopyIp: ((String) -> Unit)? = null,
    onWol: ((String, String) -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    var showPortInfo by remember { mutableStateOf<PortInfo?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect?.invoke() },
                onLongClick = { onToggleSelect?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // ─── IP line with inline port chips ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                // IP clickable opens http://ip/ or http://ip:port/ if port available
                Text(
                    text = host.ip,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !selectionMode) {
                        val firstPort = host.openPorts.firstOrNull()
                        val url = if (firstPort != null) {
                            "http://${host.ip}:${firstPort.port}/"
                        } else {
                            "http://${host.ip}/"
                        }
                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                    }
                )
                if (host.latencyMs != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${host.latencyMs}ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (host.latencyMs < 10) StatusGreen
                        else if (host.latencyMs < 50) StatusOrange
                        else StatusRed
                    )
                }
                if (isSelected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                if (onCopyIp != null) {
                    IconButton(onClick = { onCopyIp(host.ip) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = TextSecondary)
                    }
                }
                if (host.macAddress != null && onWol != null) {
                    IconButton(onClick = { onWol(host.ip, host.macAddress) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(14.dp), tint = AccentGreen)
                    }
                }
            }

            // ─── MAC & Hostname ───
            if (host.macAddress != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, null, Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = host.macAddress + (host.macVendor?.let { " ($it)" } ?: ""),
                        fontSize = 11.sp, color = TextSecondary
                    )
                }
            }
            if (!host.hostname.isNullOrBlank() && host.hostname != host.ip) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(host.hostname, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // ─── Ports as inline clickable ip:port chips ───
            if (host.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Divider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))

                // Show ports as wrapped row of clickable chips
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    host.openPorts.forEach { port ->
                        PortChip(ip = host.ip, port = port, selectionMode = selectionMode,
                            onLongPress = { showPortInfo = port })
                    }
                }
            }
        }
    }

    showPortInfo?.let { port ->
        PortInfoDialog(port = port) { showPortInfo = null }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortChip(ip: String, port: PortInfo, selectionMode: Boolean = false, onLongPress: (() -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    val scheme = if (port.port == 443 || port.port == 8443) "https" else "http"
    val url = "$scheme://$ip:${port.port}/"

    Surface(
        modifier = Modifier
            .combinedClickable(
                enabled = !selectionMode,
                onClick = { try { uriHandler.openUri(url) } catch (_: Exception) {} },
                onLongClick = onLongPress
            ),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            val icon = getPortIcon(port)
            Icon(icon, null, Modifier.size(12.dp), tint = AccentGreen)
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${ip}:${port.port}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            if (port.service != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = port.service!!,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PortInfoDialog(port: PortInfo, onDismiss: () -> Unit) {
    val description = PortDescriptions.get(port.port)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Port ${port.port}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Service: ${port.service ?: "Unknown"}", fontSize = 14.sp)
                if (description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Info: $description", fontSize = 13.sp, color = TextSecondary)
                }
                if (port.banner != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Banner: ${port.banner}", fontSize = 11.sp, color = TextSecondary,
                        fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun getPortIcon(port: PortInfo) = when {
    port.service?.contains("HTTP", true) == true -> Icons.Default.Public
    port.service?.contains("Camera", true) == true ||
    port.service?.contains("Hikvision", true) == true ||
    port.service?.contains("Dahua", true) == true ||
    port.service?.contains("ONVIF", true) == true ||
    port.service?.contains("RTSP", true) == true -> Icons.Default.Videocam
    port.service?.contains("Router", true) == true ||
    port.service?.contains("MikroTik", true) == true ||
    port.service?.contains("Winbox", true) == true -> Icons.Default.Router
    port.service?.contains("FTP", true) == true ||
    port.service?.contains("SMB", true) == true ||
    port.service?.contains("NFS", true) == true -> Icons.Default.Folder
    port.service?.contains("SSH", true) == true ||
    port.service?.contains("Telnet", true) == true -> Icons.Default.Terminal
    port.port == 443 || port.port == 8443 || port.port == 80 || port.port == 8080 -> Icons.Default.Public
    else -> Icons.Default.RadioButtonChecked
}

@Composable
fun UrlResultsList(urls: List<UrlDiscovery>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    Column(modifier = modifier) {
        urls.forEach { url ->
            UrlCard(url)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun UrlCard(url: UrlDiscovery) {
    val uriHandler = LocalUriHandler.current
    val statusColor = when (url.statusCode) {
        200 -> StatusGreen; 301, 302 -> StatusBlue; 401, 403 -> StatusOrange; 500 -> StatusRed
        else -> TextSecondary
    }
    val icon = when (url.statusCode) {
        200 -> Icons.Default.CheckCircle; 301, 302 -> Icons.Default.Public
        401, 403 -> Icons.Default.Lock; else -> Icons.Default.Public
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = statusColor)
            Spacer(Modifier.width(6.dp))
            Text("${url.statusCode}", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, color = statusColor)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(url.url, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { try { uriHandler.openUri(url.url) } catch (_: Exception) {} })
                if (url.title != null) Text(url.title, fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
