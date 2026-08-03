package com.tasirin.network.radar.util

import java.io.BufferedReader
import java.io.InputStreamReader

object PingUtil {

    /** Hasil ping lengkap: latency (ms) + TTL. */
    data class PingProbe(val latencyMs: Long, val ttl: Int?)

    /**
     * Ping a single IP and return latency in ms, or null if unreachable.
     * Uses shell ping command for accurate ICMP ping.
     */
    fun ping(ip: String, timeoutMs: Int = 1000): Long? = pingProbe(ip, timeoutMs)?.latencyMs

    /** Ping + ambil TTL untuk deteksi OS. */
    fun pingProbe(ip: String, timeoutMs: Int = 1000): PingProbe? {
        return try {
            // -W memakai satuan detik: clamp minimal 1 agar level cepat (mis. 150ms)
            // tidak menghasilkan "-W 0" yang bisa berarti tanpa batas / tidak konsisten.
            val secs = (timeoutMs.coerceAtLeast(1000) / 1000).toString()
            val process = ProcessBuilder(
                "ping", "-c", "1", "-W", secs, ip
            ).redirectErrorStream(true).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Parse: "icmp_seq=1 ttl=64 time=2.34 ms"
                val latency = Regex("""time[=:]\s*(\d+(?:\.\d+)?)\s*ms""").find(output)
                    ?.groupValues?.get(1)?.toFloat()?.let { (it * 10).toLong() / 10 } ?: 0L
                val ttl = Regex("""ttl[=:]\s*(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
                PingProbe(latency, ttl)
            } else null
        } catch (_: Exception) { null }
    }
}
