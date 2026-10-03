package com.tasirin.network.radar.util

object PingUtil {

    /** Hasil ping lengkap: latency (ms) + TTL. */
    data class PingProbe(val latencyMs: Long, val ttl: Int?)

    // Regex dikompilasi SEKALI, bukan tiap ping — ping dipanggil per host
    // (PingSweep), per siklus monitor, dan tiap 5 detik oleh monitor gateway.
    private val LATENCY_REGEX = Regex("""time[=:]\s*(\d+(?:\.\d+)?)\s*ms""")
    private val TTL_REGEX = Regex("""ttl[=:]\s*(\d+)""")

    /** Ping + ambil TTL untuk deteksi OS. */
    fun pingProbe(ip: String, timeoutMs: Int = 1000): PingProbe? {
        return try {
            // -W memakai satuan detik (bulatkan ke atas, minimal 1 agar tidak
            // menjadi "-W 0" yang perilakunya tidak konsisten antar perangkat).
            // Timeout presisi di bawah 1 detik (mis. level CEPAT 150ms) ditegakkan
            // via waitFor(timeoutMs): proses ping dihentikan sendiri begitu
            // batas lewat, jadi monitor tidak tertahan 1 detik per host mati.
            val budgetMs = timeoutMs.coerceIn(200, 10_000)
            val secs = ((budgetMs + 999) / 1000).toString()
            val process = ProcessBuilder(
                "ping", "-c", "1", "-W", secs, ip
            ).redirectErrorStream(true).start()
            try {
                val finished = process.waitFor(budgetMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) return null // lewat budget → anggap tidak merespons
                val output = try {
                    process.inputStream.bufferedReader().readText()
                } catch (_: Exception) { "" }
                val exitCode = try { process.exitValue() } catch (_: Exception) { return null }
                if (exitCode == 0) {
                    // Parse: "icmp_seq=1 ttl=64 time=2.34 ms"
                    val latency = LATENCY_REGEX.find(output)
                        ?.groupValues?.get(1)?.toFloat()?.let { (it * 10).toLong() / 10 } ?: 0L
                    val ttl = TTL_REGEX.find(output)?.groupValues?.get(1)?.toIntOrNull()
                    PingProbe(latency, ttl)
                } else null
            } finally {
                process.destroy()
            }
        } catch (_: Exception) { null }
    }
}
