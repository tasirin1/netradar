package com.tasirin.network.radar.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    fun buildMagicPacket(mac: String): ByteArray? {
        val macBytes = try {
            mac.replace("-", ":").split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            return null
        }
        if (macBytes.size != 6) return null
        return ByteArray(102).apply {
            for (i in 0 until 6) this[i] = 0xFF.toByte()
            for (i in 0 until 16) System.arraycopy(macBytes, 0, this, 6 + i * 6, 6)
        }
    }

    fun wake(ip: String, mac: String, port: Int = 9): Boolean {
        val packet = buildMagicPacket(mac) ?: return false
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                // Kirim ke broadcast global dan subnet broadcast agar pasti sampai
                val targets = buildList {
                    add("255.255.255.255")
                    getSubnetBroadcast(ip)?.let { add(it) }
                }
                var sent = false
                for (target in targets) {
                    try {
                        val addr = InetAddress.getByName(target)
                        socket.send(DatagramPacket(packet, packet.size, addr, port))
                        sent = true
                    } catch (_: Exception) { }
                }
                sent
            }
        } catch (_: Exception) { false }
    }

    /** Hitung broadcast address subnet dari IP target (asumsi /24). */
    private fun getSubnetBroadcast(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }
}
