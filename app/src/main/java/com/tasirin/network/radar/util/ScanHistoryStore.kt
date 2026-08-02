package com.tasirin.network.radar.util

import android.content.Context
import com.tasirin.network.radar.model.ScanHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/** Simpan riwayat scan (maks 100 entri) agar tidak hilang saat app ditutup. */
object ScanHistoryStore {

    private const val FILE = "netradar_history.json"
    private const val MAX_ENTRIES = 100

    fun load(context: Context): List<ScanHistoryEntry> = try {
        val text = context.openFileInput(FILE).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            try {
                ScanHistoryEntry(
                    time = o.getLong("time"),
                    type = o.getString("type"),
                    target = o.getString("target"),
                    hostCount = o.getInt("hostCount"),
                    portCount = o.getInt("portCount"),
                    durationMs = o.getLong("durationMs")
                )
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    fun save(context: Context, history: List<ScanHistoryEntry>) {
        try {
            val arr = JSONArray()
            history.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("time", e.time)
                        .put("type", e.type)
                        .put("target", e.target)
                        .put("hostCount", e.hostCount)
                        .put("portCount", e.portCount)
                        .put("durationMs", e.durationMs)
                )
            }
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { out ->
                out.write(arr.toString().toByteArray())
            }
        } catch (_: Exception) { }
    }

    /** Tambah entri baru di depan, batasi jumlah maksimal. */
    fun record(history: List<ScanHistoryEntry>, entry: ScanHistoryEntry): List<ScanHistoryEntry> =
        (listOf(entry) + history).take(MAX_ENTRIES)
}
