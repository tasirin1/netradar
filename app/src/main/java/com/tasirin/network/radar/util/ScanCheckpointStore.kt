package com.tasirin.network.radar.util

import android.content.Context
import org.json.JSONObject

/** Simpan posisi scan terakhir agar bisa dilanjutkan setelah stop / app ditutup. */
object ScanCheckpointStore {

    private const val PREFS = "netradar_checkpoint"
    private const val KEY = "checkpoint"

    data class Checkpoint(
        val target: String,
        val type: String,
        val subnetIndex: Int,
        val hostOffset: Int,
        val total: Long,
        val lastIp: String,
        val updatedAt: Long
    )

    fun save(context: Context, cp: Checkpoint?) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (cp == null) {
                prefs.edit().remove(KEY).apply()
                return
            }
            val json = JSONObject()
                .put("target", cp.target)
                .put("type", cp.type)
                .put("subnetIndex", cp.subnetIndex)
                .put("hostOffset", cp.hostOffset)
                .put("total", cp.total)
                .put("lastIp", cp.lastIp)
                .put("updatedAt", cp.updatedAt)
            prefs.edit().putString(KEY, json.toString()).apply()
        } catch (_: Exception) { }
    }

    fun load(context: Context): Checkpoint? = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrBlank()) null
        else {
            val o = JSONObject(raw)
            Checkpoint(
                target = o.optString("target"),
                type = o.optString("type"),
                subnetIndex = o.optInt("subnetIndex", 0),
                hostOffset = o.optInt("hostOffset", 0),
                total = o.optLong("total", 0),
                lastIp = o.optString("lastIp"),
                updatedAt = o.optLong("updatedAt", 0)
            )
        }
    } catch (_: Exception) { null }
}
