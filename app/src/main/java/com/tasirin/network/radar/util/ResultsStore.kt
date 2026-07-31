package com.tasirin.network.radar.util

import android.content.Context
import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.PortInfo
import com.tasirin.network.radar.model.UrlDiscovery
import org.json.JSONArray
import org.json.JSONObject

/** Persist hasil scan (host + URL) ke SharedPreferences agar tidak hilang saat app ditutup / scan baru. */
object ResultsStore {

    private const val PREFS = "netradar_results"
    private const val KEY_HOSTS = "hosts"
    private const val KEY_URLS = "urls"

    fun save(context: Context, hosts: Collection<HostInfo>, urls: Collection<UrlDiscovery>) {
        try {
            val hostsJson = JSONArray()
            hosts.forEach { hostsJson.put(hostToJson(it)) }
            val urlsJson = JSONArray()
            urls.forEach { urlsJson.put(urlToJson(it)) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_HOSTS, hostsJson.toString())
                .putString(KEY_URLS, urlsJson.toString())
                .apply()
        } catch (_: Exception) { }
    }

    fun load(context: Context): Pair<List<HostInfo>, List<UrlDiscovery>> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            hostFromJson(prefs.getString(KEY_HOSTS, null)) to urlFromJson(prefs.getString(KEY_URLS, null))
        } catch (_: Exception) {
            emptyList<HostInfo>() to emptyList<UrlDiscovery>()
        }
    }

    private fun hostToJson(host: HostInfo): JSONObject = JSONObject().apply {
        put("ip", host.ip)
        host.hostname?.let { put("hostname", it) }
        host.macAddress?.let { put("mac", it) }
        host.macVendor?.let { put("vendor", it) }
        host.latencyMs?.let { put("latency", it) }
        put("alive", host.isAlive)
        val ports = JSONArray()
        host.openPorts.forEach { p ->
            ports.put(JSONObject().apply {
                put("port", p.port)
                p.service?.let { put("service", it) }
                p.banner?.let { put("banner", it) }
            })
        }
        put("ports", ports)
    }

    private fun urlToJson(url: UrlDiscovery): JSONObject = JSONObject().apply {
        put("url", url.url)
        put("code", url.statusCode)
        url.title?.let { put("title", it) }
    }

    private fun hostFromJson(raw: String?): List<HostInfo> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ip = o.optString("ip")
                if (ip.isEmpty()) continue
                val portsJson = o.optJSONArray("ports") ?: JSONArray()
                val ports = buildList {
                    for (j in 0 until portsJson.length()) {
                        val po = portsJson.optJSONObject(j) ?: continue
                        val port = po.optInt("port", -1)
                        if (port > 0) add(PortInfo(port, po.optString("service").ifEmpty { null },
                            po.optString("banner").ifEmpty { null }))
                    }
                }
                add(HostInfo(
                    ip = ip,
                    hostname = o.optString("hostname").ifEmpty { null },
                    macAddress = o.optString("mac").ifEmpty { null },
                    macVendor = o.optString("vendor").ifEmpty { null },
                    latencyMs = if (o.has("latency")) o.optLong("latency") else null,
                    isAlive = o.optBoolean("alive", true),
                    openPorts = ports
                ))
            }
        }
    }

    private fun urlFromJson(raw: String?): List<UrlDiscovery> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isEmpty()) continue
                add(UrlDiscovery(url, o.optInt("code", 0), o.optString("title").ifEmpty { null }))
            }
        }
    }
}
