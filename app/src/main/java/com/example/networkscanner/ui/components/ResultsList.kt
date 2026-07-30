package com.example.networkscanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networkscanner.model.HostInfo
import com.example.networkscanner.model.PortInfo
import com.example.networkscanner.model.UrlDiscovery
import com.example.networkscanner.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostResultsList(
    hosts: List<HostInfo>,
    modifier: Modifier = Modifier
) {
    if (hosts.isEmpty()) return
    Column(modifier = modifier) {
        hosts.forEach { host ->
            HostCard(host = host)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun HostCard(host: HostInfo) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                val uriHandler = LocalUriHandler.current
                Text(
                    text = host.ip,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { 
                        uriHandler.openUri("http://${host.ip}/")
                    }
                )
                if (host.latencyMs != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${host.latencyMs}ms",
                        fontSize = 11.sp,
                        color = if (host.latencyMs < 10) StatusGreen
                        else if (host.latencyMs < 50) StatusOrange
                        else StatusRed
                    )
                }
                Spacer(Modifier.weight(1f))
                if (host.openPorts.isNotEmpty()) {
                    Text(
                        text = "${host.openPorts.size} port(s)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                }
            }

            if (host.macAddress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, null, Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = host.macAddress + (host.macVendor?.let { " ($it)" } ?: ""),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (!host.hostname.isNullOrBlank() && host.hostname != host.ip) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(host.hostname, fontSize = 11.sp, color = TextSecondary)
                }
            }

            if (expanded && host.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Divider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                host.openPorts.forEach { port ->
                    PortRow(port)
                }
            }
        }
    }
}

@Composable
fun PortRow(port: PortInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        val icon = getPortIcon(port)
        Icon(icon, null, Modifier.size(14.dp), tint = AccentGreen)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${port.port}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = port.service ?: "Unknown",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (port.banner != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = port.banner.take(40),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
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
    else -> Icons.Default.RadioButtonChecked
}

@Composable
fun UrlResultsList(
    urls: List<UrlDiscovery>,
    modifier: Modifier = Modifier
) {
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
    val statusColor = when (url.statusCode) {
        200 -> StatusGreen
        301, 302 -> StatusBlue
        401, 403 -> StatusOrange
        500 -> StatusRed
        else -> TextSecondary
    }
    val icon = when (url.statusCode) {
        200 -> Icons.Default.CheckCircle
        301, 302 -> Icons.Default.Http // Note: Redirect might need extended icons
        401, 403 -> Icons.Default.Lock
        else -> Icons.Default.Http
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = statusColor)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${url.statusCode}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = statusColor
            )
            Spacer(Modifier.width(6.dp))
            Column {
                val uriHandler = LocalUriHandler.current
                Text(
                    text = url.url,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { 
                        try { uriHandler.openUri(url.url) } catch (_: Exception) {}
                    }
                )
                if (url.title != null) {
                    Text(
                        text = url.title,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
