package com.tasirin.network.radar.util

import android.content.Context
import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.PortInfo
import com.tasirin.network.radar.model.ScanHistoryEntry
import com.tasirin.network.radar.model.ThemeMode
import com.tasirin.network.radar.model.UrlDiscovery
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class NetRadarBackup(
    val settings: AppSettings,
    val hosts: List<HostInfo>,
    val urls: List<UrlDiscovery>,
    val favorites: Set<String>,
    val history: List<ScanHistoryEntry>
)

/** Serializer dan pemulih backup JSON untuk hasil scan serta pengaturan. */
object BackupManager {

    private const val SCHEMA = 1

    fun toJson(backup: NetRadarBackup): String = JSONObject().apply {
        put("schema", SCHEMA)
        put("app", "NetRadar")
        put("settings", settingsJson(backup.settings))
        put("hosts", hostArray(backup.hosts))
        put("urls", urlArray(backup.urls))
        put("favorites", JSONArray(backup.favorites))
        put("history", historyArray(backup.history))
    }.toString(2)

    fun parse(text: String): NetRadarBackup {
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA || root.optString("app") != "NetRadar") {
            throw IllegalArgumentException("Format backup tidak didukung")
        }
        return NetRadarBackup(
            settings = settingsFrom(root.optJSONObject("settings") ?: JSONObject()),
            hosts = hostsFrom(root.optJSONArray("hosts") ?: JSONArray()),
            urls = urlsFrom(root.optJSONArray("urls") ?: JSONArray()),
            favorites = root.optJSONArray("favorites")?.let { array ->
                buildSet { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
            } ?: emptySet(),
            history = historyFrom(root.optJSONArray("history") ?: JSONArray())
        )
    }

    fun createFile(context: Context, backup: NetRadarBackup): File {
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        return File(dir, "netradar-backup.json").apply { writeText(toJson(backup)) }
    }

    fun restore(context: Context, text: String): NetRadarBackup {
        val backup = parse(text)
        SettingsStore.save(context, backup.settings)
        ResultsStore.save(context, backup.hosts, backup.urls)
        FavoritesStore.save(context, backup.favorites)
        ScanHistoryStore.save(context, backup.history)
        return backup
    }

    private fun settingsJson(value: AppSettings) = JSONObject().apply {
        put("theme", value.themeMode.storageName)
        put("customPorts", value.customPorts)
        put("notifyNew", value.notifyNewDevices)
        put("notifyImportant", value.notifyImportantOffline)
        put("notifyDone", value.notifyScanDone)
        put("keepScreenOn", value.keepScreenOn)
        put("sound", value.soundEnabled)
        put("autoDiff", value.autoDiffDialog)
        put("compact", value.compactMode)
        put("monitorFavoritesOnly", value.monitorFavoritesOnly)
    }

    private fun settingsFrom(value: JSONObject): AppSettings {
        val mode = ThemeMode.from(value.optString("theme", "system"))
        return AppSettings(
            darkTheme = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.SYSTEM -> null
            },
            themeMode = mode,
            customPorts = value.optString("customPorts"),
            notifyNewDevices = value.optBoolean("notifyNew", true),
            notifyImportantOffline = value.optBoolean("notifyImportant", true),
            notifyScanDone = value.optBoolean("notifyDone", true),
            keepScreenOn = value.optBoolean("keepScreenOn", true),
            soundEnabled = value.optBoolean("sound", true),
            autoDiffDialog = value.optBoolean("autoDiff", true),
            compactMode = value.optBoolean("compact", false),
            monitorFavoritesOnly = value.optBoolean("monitorFavoritesOnly", false)
        )
    }

    private fun hostArray(hosts: List<HostInfo>) = JSONArray().apply {
        hosts.forEach { host ->
            put(JSONObject().apply {
                put("ip", host.ip)
                host.hostname?.let { put("hostname", it) }
                host.label?.let { put("label", it) }
                host.macAddress?.let { put("mac", it) }
                host.macVendor?.let { put("vendor", it) }
                host.latencyMs?.let { put("latency", it) }
                put("alive", host.isAlive)
                if (host.ipConflict) put("conflict", true)
                if (host.lastSeenScan > 0) put("seen", host.lastSeenScan)
                put("ports", JSONArray().apply {
                    host.openPorts.forEach { port ->
                        put(JSONObject().apply {
                            put("port", port.port)
                            port.service?.let { put("service", it) }
                            port.banner?.let { put("banner", it) }
                        })
                    }
                })
            })
        }
    }

    private fun hostsFrom(array: JSONArray) = buildList {
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            val ip = value.optString("ip")
            if (ip.isBlank()) continue
            val portsJson = value.optJSONArray("ports") ?: JSONArray()
            add(HostInfo(
                ip = ip,
                hostname = value.optString("hostname").ifBlank { null },
                label = value.optString("label").ifBlank { null },
                macAddress = value.optString("mac").ifBlank { null },
                macVendor = value.optString("vendor").ifBlank { null },
                latencyMs = if (value.has("latency")) value.optLong("latency") else null,
                isAlive = value.optBoolean("alive", true),
                ipConflict = value.optBoolean("conflict", false),
                lastSeenScan = value.optLong("seen", 0),
                openPorts = buildList {
                    for (j in 0 until portsJson.length()) {
                        val portValue = portsJson.optJSONObject(j) ?: continue
                        val port = portValue.optInt("port")
                        if (port in 1..65535) add(PortInfo(
                            port = port,
                            service = portValue.optString("service").ifBlank { null },
                            banner = portValue.optString("banner").ifBlank { null }
                        ))
                    }
                }
            ))
        }
    }

    private fun urlArray(urls: List<UrlDiscovery>) = JSONArray().apply {
        urls.forEach { put(JSONObject().put("url", it.url).put("code", it.statusCode).putOpt("title", it.title)) }
    }

    private fun urlsFrom(array: JSONArray) = buildList {
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            val url = value.optString("url")
            if (url.isNotBlank()) add(UrlDiscovery(url, value.optInt("code"), value.optString("title").ifBlank { null }))
        }
    }

    private fun historyArray(history: List<ScanHistoryEntry>) = JSONArray().apply {
        history.forEach {
            put(JSONObject()
                .put("time", it.time).put("type", it.type).put("target", it.target)
                .put("hostCount", it.hostCount).put("portCount", it.portCount)
                .put("durationMs", it.durationMs))
        }
    }

    private fun historyFrom(array: JSONArray) = buildList {
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i) ?: continue
            try {
                add(ScanHistoryEntry(
                    time = value.getLong("time"),
                    type = value.getString("type"),
                    target = value.getString("target"),
                    hostCount = value.getInt("hostCount"),
                    portCount = value.getInt("portCount"),
                    durationMs = value.getLong("durationMs")
                ))
            } catch (_: Exception) {
                android.util.Log.w("NetRadarBackup", "Entri riwayat backup tidak valid dilewati")
            }
        }
    }
}
