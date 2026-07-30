package com.example.networkscanner.util

import java.io.BufferedReader
import java.io.FileReader

object MacVendorLookup {

    // Simplified OUI vendor database (commonly found in home networks)
    private val vendorMap = mapOf(
        "00:1A:2B" to "Cisco",
        "00:1B:21" to "D-Link",
        "00:0C:41" to "Netgear",
        "00:14:BF" to "TP-Link",
        "00:1A:3F" to "Huawei",
        "00:1D:0E" to "Xiaomi",
        "00:23:CD" to "ASUS",
        "00:17:C8" to "Belkin",
        "00:04:ED" to "Zyxel",
        "00:1A:E9" to "Buffalo",
        "00:24:01" to "Samsung",
        "00:1E:58" to "Apple",
        "00:1A:92" to "Sony",
        "00:1F:5B" to "Intel",
        "00:1B:11" to "Microsoft",
        "00:15:E9" to "Linksys",
        "00:22:6B" to "Ubiquiti",
        "00:1C:10" to "MikroTik",
        "00:0E:8F" to "Hikvision",
        "00:1C:CF" to "Dahua",
        "00:1B:44" to "Axis",
        "00:18:02" to "Foscam",
        "00:1E:06" to "Aruba",
        "00:1A:8C" to "Ruckus",
        "00:23:DF" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "DC:A6:32" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "00:0C:29" to "VMware",
        "00:50:56" to "VMware",
        "00:15:5D" to "Microsoft Hyper-V",
        "00:1C:42" to "Parallels",
        "3C:07:54" to "Intel",
        "C8:3A:35" to "Intel",
        "A8:93:4A" to "Samsung",
        "F0:9F:C2" to "Samsung",
        "B0:75:D5" to "OnePlus",
        "AC:84:C6" to "Xiaomi",
        "58:CB:52" to "Huawei",
        "68:8F:84" to "Apple",
        "F8:2F:A8" to "Apple",
        "84:38:35" to "HP",
        "00:26:AB" to "Dell",
        "A4:4E:31" to "Lenovo",
        "28:D2:44" to "Amazon",
        "74:C2:2B" to "Amazon",
        "10:AE:60" to "Google",
        "18:1E:78" to "Google",
        "E0:AC:CB" to "ONVIF",
        "00:12:47" to "EDIMAX",
        "00:1A:4A" to "Tenda",
        "A0:21:B7" to "Tenda",
    )

    fun lookup(mac: String?): String? {
        if (mac == null) return null
        val key = mac.uppercase().take(8) // "XX:XX:XX"
        return vendorMap.entries.firstOrNull { (oui) ->
            key.startsWith(oui.uppercase().take(8))
        }?.value
    }

    fun lookupFull(mac: String?): String? {
        if (mac == null) return null
        // Try full MAC first (8 chars), then first 8 chars
        val normalized = mac.uppercase().replace("-", ":").replace(".", ":")
        for (len in listOf(8, 7, 5)) {
            val prefix = normalized.take(len)
            val result = vendorMap[prefix] ?: vendorMap.entries.firstOrNull {
                it.key.uppercase().take(len) == prefix
            }?.value
            if (result != null) return result
        }
        return null
    }

    /**
     * Read ARP table to get MAC addresses for IPs on the local network.
     * Format of /proc/net/arp:
     * IP address       HW type     Flags       HW address            Mask     Device
     * 192.168.1.1      0x1         0x2         00:11:22:33:44:55     *        wlan0
     */
    fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac.count { it == ':' } == 5) {
                            result[ip] = mac.uppercase()
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return result
    }
}
