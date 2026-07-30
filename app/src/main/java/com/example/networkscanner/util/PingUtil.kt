package com.tasirin.network.radar.util

import java.io.BufferedReader
import java.io.InputStreamReader

object PingUtil {

    /**
     * Ping a single IP and return latency in ms, or null if unreachable.
     * Uses shell ping command for accurate ICMP ping.
     */
    fun ping(ip: String, timeoutMs: Int = 1000): Long? {
        return try {
            val process = ProcessBuilder(
                "ping", "-c", "1", "-W", (timeoutMs / 1000).toString(), ip
            ).redirectErrorStream(true).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Parse: "icmp_seq=1 ttl=64 time=2.34 ms"
                val timeMatch = Regex("""time[=:]\s*(\d+(?:\.\d+)?)\s*ms""").find(output)
                timeMatch?.groupValues?.get(1)?.toFloat()?.let { (it * 10).toLong() / 10 } ?: 0L
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * TCP connect check (fallback when ICMP ping not available).
     */
    fun tcpPing(ip: String, port: Int = 80, timeoutMs: Int = 500): Long? {
        return try {
            val start = System.currentTimeMillis()
            val sock = java.net.Socket()
            sock.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            sock.close()
            System.currentTimeMillis() - start
        } catch (_: Exception) { null }
    }
}
