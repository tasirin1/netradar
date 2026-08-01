package com.tasirin.network.radar.util

import android.content.Context
import org.json.JSONArray

/** Simpan daftar IP perangkat penting (favorit) agar bertahan antar sesi. */
object FavoritesStore {

    private const val PREFS = "netradar_favorites"
    private const val KEY_IPS = "ips"

    fun load(context: Context): Set<String> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_IPS, null)
        if (raw.isNullOrBlank()) emptySet()
        else JSONArray(raw).let { arr ->
            buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
        }
    } catch (_: Exception) { emptySet() }

    fun save(context: Context, ips: Set<String>) {
        try {
            val arr = JSONArray()
            ips.forEach { arr.put(it) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_IPS, arr.toString()).apply()
        } catch (_: Exception) { }
    }
}
