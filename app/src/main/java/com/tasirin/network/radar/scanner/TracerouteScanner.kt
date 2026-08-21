package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.ScanEvent
import com.tasirin.network.radar.model.ScanResult
import com.tasirin.network.radar.model.ScanType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

/**
 * Traceroute non-root: jalankan "ping -c 1 -t <ttl>" per hop.
 * Router yang TTL-nya habis membalas "Time to live exceeded" → hop terdeteksi.
 * Hop yang tidak merespon dianggap timeout; berhenti setelah 3 timeout beruntun
 * atau saat target membalas.
 */
class TracerouteScanner {

    private val maxHops = 30
    private val maxTimeouts = 3

    suspend fun scan(target: String): Flow<ScanEvent> = flow {
        val targetIp = try {
            InetAddress.getByName(target.substringBefore(":")).hostAddress
        } catch (_: Exception) { null }
        if (targetIp == null) {
            emit(ScanEvent.Error("Cannot resolve target: $target"))
            return@flow
        }

        emit(ScanEvent.Progress("Traceroute: $targetIp", 0, maxHops))
        var timeouts = 0

        for (ttl in 1..maxHops) {
            ScanPause.checkPause()
            val hop = probeHop(targetIp, ttl)
            emit(ScanEvent.Progress("Hop $ttl: ${hop.ip ?: "(no response)"}", ttl, maxHops))

            if (hop.ip == null) {
                if (++timeouts >= maxTimeouts) break
                continue
            }
            timeouts = 0

            val hostname = reverseLookup(hop.ip)
            emit(ScanEvent.HostFound(HostInfo(ip = hop.ip, hostname = hostname, latencyMs = hop.latencyMs)))

            if (hop.latencyMs != null && hop.ip == targetIp) break  // target tercapai
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.TRACE, target = target)))
    }

    private data class Hop(val ip: String?, val latencyMs: Long?)

    private suspend fun probeHop(targetIp: String, ttl: Int): Hop = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "ping", "-c", "1", "-t", ttl.toString(), "-W", "1", targetIp
        ).redirectErrorStream(true).start()
        try {
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            try {
                process.waitFor()
                parseHop(output)
            } finally {
                process.destroy()
            }
        } catch (_: Exception) { Hop(null, null) }
    }

    /** Baca output ping: balasan akhir "bytes from <ip> ... time=X ms" atau TTL habis "From <ip> ... exceeded". */
    private fun parseHop(output: String): Hop {
        REPLY_REGEX.find(output)?.let { m ->
            val time = m.groupValues[2].toFloatOrNull()?.let { (it * 10).toLong() / 10 }
            return Hop(m.groupValues[1], time)
        }
        EXCEEDED_REGEX.find(output)?.let { m -> return Hop(m.groupValues[1], null) }
        return Hop(null, null)
    }

    private suspend fun reverseLookup(ip: String): String? = try {
        withTimeout(300) { InetAddress.getByName(ip).hostName }.let { if (it != ip) it else null }
    } catch (_: Exception) { null }

    private companion object {
        val REPLY_REGEX = Regex("""bytes from ([0-9.]+)[^\n]*?time[=:]\s*([0-9.]+)\s*ms""")
        val EXCEEDED_REGEX = Regex("""(?i)\bfrom ([0-9.]+)\s*:.*(?:exceeded|time to live)""")
    }
}
