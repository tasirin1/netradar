package com.tasirin.network.radar.util

/**
 * Tebakan sistem operasi/jenis perangkat dari TTL ping + port yang terbuka.
 * TTL awal umum: 64 (Linux/Android/macOS), 128 (Windows), 255 (router/Cisco).
 */
object OsDetector {

    fun guess(ttl: Int?, openPorts: List<Int>): String? {
        val ports = openPorts.toSet()
        return when {
            ports.any { it in ROUTER_PORTS } -> "Router/Network"
            ports.any { it in CAMERA_PORTS } -> "Camera Device"
            ttl == null -> null
            ttl <= 32 -> "Unix"
            ttl <= 64 -> "Linux/Android"
            ttl <= 128 -> "Windows"
            else -> "Network Device"
        }
    }

    private val ROUTER_PORTS = setOf(8291, 7547, 5000, 23, 161, 1900, 2601, 2602)
    private val CAMERA_PORTS = setOf(554, 8554, 34567, 37777, 37215, 8899, 7070, 6666)
}
