package com.example.networkscanner.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    fun wake(ip: String, mac: String, port: Int = 9): Boolean {
        return try {
            val macBytes = mac.replace("-", ":").split(":").map { it.toInt(16).toByte() }.toByteArray()
            val packet = ByteArray(102)
            // 6 bytes of 0xFF
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            // 16 repetitions of MAC
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }
            val address = InetAddress.getByName(ip)
            val dp = DatagramPacket(packet, packet.size, address, port)
            DatagramSocket().use { socket ->
                socket.send(dp)
            }
            true
        } catch (_: Exception) { false }
    }
}
