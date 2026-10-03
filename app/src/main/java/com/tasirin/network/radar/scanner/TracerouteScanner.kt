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

            // Target tercapai bila hop == target, tanpa syarat latency: target yang
            // memfilter ping/firewall tidak membalas sehingga balasan akhir tak
            // pernah ber-latency — tanpa ini trace selalu jalan penuh 30 hop.
            if (hop.ip == targetIp || hop.ip == target) break  // target tercapai
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.TRACE, target = target)))
    }

    private data class Hop(val ip: String?, val latencyMs: Long?)

    private suspend fun probeHop(targetIp: String, ttl: Int): Hop = withContext(Dispatchers.IO) {
        // waitFor ber-timeout seperti PingUtil: ping yang macet tidak boleh
        // menggantung coroutine selamanya. Output dibaca SETELAH proses
        // selesai agar pipe penuh tak deadlock sebelum waitFor kembali.
        var process: Process? = null
        try {
            process = ProcessBuilder(
                "ping", "-c", "1", "-t", ttl.toString(), "-W", "1", targetIp
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) return@withContext Hop(null, null)
            val output = try {
                BufferedReader(InputStreamReader(process.inputStream)).readText()
            } catch (_: Exception) { "" }
            parseHop(output)
        } catch (_: Exception) { Hop(null, null) }
        finally { try { process?.destroy() } catch (_: Exception) {} }
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
        // Kelas [0-9a-fA-F:.]+ mencakup IPv4 dan IPv6 (mis. "bytes from 2001:db8::1 ...").
        val REPLY_REGEX = Regex("""bytes from ([0-9a-fA-F:.]+)[^\n]*?time[=:]\s*([0-9.]+)\s*ms""")
        // Titik-dua setelah IP opsional: sebagian ping menulis
        // "From 1.2.3.4 icmp_seq=1 Time to live exceeded" tanpa ":".
        val EXCEEDED_REGEX = Regex("""(?i)\bfrom ([0-9a-fA-F:.]+)\s*:?.*(?:exceeded|time to live)""")
    }
}
