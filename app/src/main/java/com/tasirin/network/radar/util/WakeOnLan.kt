package com.tasirin.network.radar.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    fun wake(ip: String, mac: String, port: Int = 9): Boolean {
        return try {
            val macBytes = mac.replace("-", ":").split(":").map { it.toInt(16).toByte() }.toByteArray()
            if (macBytes.size != 6) return false
            val packet = ByteArray(102)
            // 6 bytes of 0xFF
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            // 16 repetitions of MAC
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }
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
