package com.tasirin.network.radar.util

import android.content.Context
import com.tasirin.network.radar.model.UptimeEvent
import org.json.JSONArray
import org.json.JSONObject

/** Simpan riwayat online/offline per IP (dibatasi per host). */
object UptimeStore {

    private const val PREFS = "netradar_uptime"
    private const val KEY_EVENTS = "events"
    private const val MAX_PER_HOST = 100

    fun load(context: Context): Map<String, List<UptimeEvent>> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, null)
        if (raw.isNullOrBlank()) emptyMap()
        else {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { ip ->
                ip to (obj.optJSONArray(ip)?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            val e = arr.optJSONArray(i) ?: continue
                            add(UptimeEvent(e.optLong(0), e.optInt(1) == 1))
                        }
                    }
                } ?: emptyList())
            }
        }
    } catch (_: Exception) { emptyMap() }

    /** Tambah event lalu simpan; kembalikan data terbaru. */
    fun record(context: Context, current: Map<String, List<UptimeEvent>>, ip: String, online: Boolean): Map<String, List<UptimeEvent>> {
        val events = ((current[ip] ?: emptyList()) + UptimeEvent(System.currentTimeMillis(), online)).takeLast(MAX_PER_HOST)
        val next = current + (ip to events)
        save(context, next)
        return next
    }

    fun save(context: Context, data: Map<String, List<UptimeEvent>>) {
        try {
            val obj = JSONObject()
            data.forEach { (ip, events) ->
                val arr = JSONArray()
                events.forEach { arr.put(JSONArray().put(it.ts).put(if (it.online) 1 else 0)) }
                obj.put(ip, arr)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_EVENTS, obj.toString()).apply()
        } catch (_: Exception) { }
    }
}
