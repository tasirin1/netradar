package com.tasirin.network.radar.util

import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.PortInfo
import com.tasirin.network.radar.model.ThemeMode
import com.tasirin.network.radar.model.UrlDiscovery
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `backup json bisa diparse kembali`() {
        val backup = NetRadarBackup(
            settings = AppSettings(
                darkTheme = true,
                themeMode = ThemeMode.AMOLED,
                customPorts = "22, 80",
                compactMode = true
            ),
            hosts = listOf(HostInfo(
                ip = "192.168.1.10",
                openPorts = listOf(PortInfo(22, "SSH")),
                ipConflict = true,
                lastSeenScan = 12
            )),
            urls = listOf(UrlDiscovery("http://192.168.1.10/admin", 200)),
            favorites = setOf("192.168.1.10"),
            history = emptyList()
        )

        val parsed = BackupManager.parse(BackupManager.toJson(backup))

        assertEquals(backup.settings.themeMode, parsed.settings.themeMode)
        assertEquals(backup.settings.customPorts, parsed.settings.customPorts)
        assertEquals(backup.hosts, parsed.hosts)
        assertEquals(backup.urls, parsed.urls)
        assertEquals(backup.favorites, parsed.favorites)
        assertEquals(ThemeMode.AMOLED.storageName, JSONObject(BackupManager.toJson(backup)).getJSONObject("settings").getString("theme"))
    }

    @Test
    fun `format asing ditolak`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.parse("""{"schema":99,"app":"Other"}""")
        }
    }
}
