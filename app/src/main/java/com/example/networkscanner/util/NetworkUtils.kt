package com.example.networkscanner.util

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException

object NetworkUtils {

    fun resolveTarget(input: String): List<String> {
        val trimmed = input.trim()
        // Check if CIDR
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val baseIp = parts[0]
            val prefix = parts[1].toIntOrNull() ?: 24
            return expandCidr(baseIp, prefix)
        }
        // Check if range like 192.168.0.1-20
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-")
            val baseParts = parts[0].trim().split(".")
            if (baseParts.size == 4) {
                val start = baseParts[3].toIntOrNull() ?: return listOf(trimmed)
                val end = parts[1].trim().toIntOrNull() ?: return listOf(trimmed)
                val prefix = baseParts.dropLast(1).joinToString(".")
                return (start..end).map { "$prefix.$it" }
            }
        }
        return listOf(trimmed)
    }

    private fun expandCidr(baseIp: String, prefix: Int): List<String> {
        try {
            val addr = InetAddress.getByName(baseIp)
            val bytes = addr.address
            val ipInt = bytesToInt(bytes)
            val bits = 32 - prefix
            if (bits <= 0 || bits > 24) return listOf(baseIp)
            val mask = if (bits >= 32) 0 else (-1 shl bits)
            val masked = ipInt and mask
            val count = 1 shl bits
            return if (count > 65536) emptyList()
            else (1 until count - 1).map { intToIp(masked + it) }
        } catch (_: Exception) {
            return listOf(baseIp)
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun intToIp(value: Int): String {
        return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }

    fun getLocalIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull {
                    !it.isLoopbackAddress && it.hostAddress?.contains('.') == true
                }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    fun getLocalNetworkPrefix(): String? {
        val ip = getLocalIp() ?: return null
        val parts = ip.split(".")
        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
        return null
    }



    /**
     * Automatically expand a single private IP to full /24 subnet
     * and include the gateway (.1).
     * For public IPs, just return the IP itself.
     */
    fun autoExpandTarget(input: String): List<String> {
        val trimmed = input.trim()
        
        // If already CIDR or range, resolve as-is
        if (trimmed.contains("/") || trimmed.contains("-")) {
            return resolveTarget(trimmed)
        }
        
        // Single IP
        val parts = trimmed.split(".")
        if (parts.size != 4) return listOf(trimmed)
        
        // Only auto-expand private IP ranges
        val first = parts[0].toIntOrNull() ?: return listOf(trimmed)
        val second = parts[1].toIntOrNull() ?: return listOf(trimmed)
        
        val isPrivate = (first == 10) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168)
        
        if (!isPrivate) return listOf(trimmed)
        
        // Expand to /24 subnet
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        return (1..254).map { "$prefix.$it" }
    }

    /**
     * Detect local gateway IP (usually .1 on the local subnet)
     */
    fun getLocalGateway(): String? {
        val prefix = getLocalNetworkPrefix() ?: return null
        return "$prefix.1"
    }

    fun getBroadcastAddress(): String? {
        val prefix = getLocalNetworkPrefix() ?: return null
        return "$prefix.255"
    }
}
