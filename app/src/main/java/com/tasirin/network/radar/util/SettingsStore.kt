package com.tasirin.network.radar.util

import android.content.Context
import android.util.Log
import com.tasirin.network.radar.model.ThemeMode

/** Preferensi aplikasi yang bisa diubah user dan bertahan antar sesi. */
data class AppSettings(
    val darkTheme: Boolean? = null,          // null = sistem, false = terang, true = gelap
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customPorts: String = "",
    val notifyNewDevices: Boolean = true,
    val notifyImportantOffline: Boolean = true,
    val notifyScanDone: Boolean = true,
    val keepScreenOn: Boolean = true,
    val soundEnabled: Boolean = true,
    val autoDiffDialog: Boolean = true,
    val compactMode: Boolean = false,
    val monitorFavoritesOnly: Boolean = false
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
            themeMode = ThemeMode.from(
                p.getString("theme_mode", null) ?: p.getString("theme", "system")
            ),
            customPorts = p.getString("custom_ports", "").orEmpty(),
            notifyNewDevices = p.getBoolean("notify_new", true),
            notifyImportantOffline = p.getBoolean("notify_important", true),
            notifyScanDone = p.getBoolean("notify_done", true),
            keepScreenOn = p.getBoolean("keep_screen_on", true),
            soundEnabled = p.getBoolean("sound_enabled", true),
            autoDiffDialog = p.getBoolean("auto_diff_dialog", true),
            compactMode = p.getBoolean("compact_mode", false),
            monitorFavoritesOnly = p.getBoolean("monitor_fav_only", false)
        )
    }

    fun save(context: Context, s: AppSettings) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("theme", when (s.themeMode) {
                    ThemeMode.LIGHT -> "light"
                    ThemeMode.DARK, ThemeMode.AMOLED -> "dark"
                    ThemeMode.SYSTEM -> "system"
                })
                .putString("theme_mode", s.themeMode.storageName)
                .putString("custom_ports", s.customPorts)
                .putBoolean("notify_new", s.notifyNewDevices)
                .putBoolean("notify_important", s.notifyImportantOffline)
                .putBoolean("notify_done", s.notifyScanDone)
                .putBoolean("keep_screen_on", s.keepScreenOn)
                .putBoolean("sound_enabled", s.soundEnabled)
                .putBoolean("auto_diff_dialog", s.autoDiffDialog)
                .putBoolean("compact_mode", s.compactMode)
                .putBoolean("monitor_fav_only", s.monitorFavoritesOnly)
                .apply()
        } catch (e: Exception) {
            Log.w("NetRadarSettings", "Pengaturan gagal disimpan", e)
        }
    }
}
