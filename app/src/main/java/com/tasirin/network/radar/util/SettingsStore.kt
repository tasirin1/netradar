package com.tasirin.network.radar.util

import android.content.Context

/** Preferensi aplikasi yang bisa diubah user dan bertahan antar sesi. */
data class AppSettings(
    val darkTheme: Boolean? = null,          // null = sistem, false = terang, true = gelap
    val notifyNewDevices: Boolean = true,
    val notifyImportantOffline: Boolean = true,
    val notifyScanDone: Boolean = true,
    val keepScreenOn: Boolean = true
)

/** Simpan preferensi aplikasi ke SharedPreferences. */
object SettingsStore {

    private const val PREFS = "netradar_settings"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            darkTheme = when (p.getString("theme", "system")) {
                "light" -> false
                "dark" -> true
                else -> null
            },
            notifyNewDevices = p.getBoolean("notify_new", true),
            notifyImportantOffline = p.getBoolean("notify_important", true),
            notifyScanDone = p.getBoolean("notify_done", true),
            keepScreenOn = p.getBoolean("keep_screen_on", true)
        )
    }

    fun save(context: Context, s: AppSettings) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("theme", when (s.darkTheme) {
                    true -> "dark"
                    false -> "light"
                    null -> "system"
                })
                .putBoolean("notify_new", s.notifyNewDevices)
                .putBoolean("notify_important", s.notifyImportantOffline)
                .putBoolean("notify_done", s.notifyScanDone)
                .putBoolean("keep_screen_on", s.keepScreenOn)
                .apply()
        } catch (_: Exception) { }
    }
}
