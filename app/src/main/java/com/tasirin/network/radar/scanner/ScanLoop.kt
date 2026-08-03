package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.OsDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.net.InetAddress

/**
 * Helper bersama untuk alur scan yang identik di semua scanner:
 * reverse DNS, pembentukan HostInfo, dan iterasi chunk paralel + progress.
 */
object ScanLoop {

    /** Reverse DNS lookup singkat; null jika gagal / timeout / sama dengan IP. */
    suspend fun hostname(ip: String): String? = try {
        withTimeout(300) { InetAddress.getByName(ip).hostName }.let { if (it != ip) it else null }
    } catch (_: Exception) { null }

    /** Bangun HostInfo dengan hostname + MAC/vendor dari tabel ARP. */
    suspend fun hostInfo(
        ip: String,
        arpTable: Map<String, String>,
        isAlive: Boolean = true,
        latencyMs: Long? = null,
        ttl: Int? = null,
        openPorts: List<PortInfo> = emptyList()
    ): HostInfo {
        val mac = arpTable[ip]
        val hostname = hostname(ip)
        return HostInfo(
            ip = ip,
            hostname = hostname,
            label = hostname?.takeIf { it != ip } ?: NetworkUtils.lookupMacVendor(mac),
            macAddress = mac,
            macVendor = NetworkUtils.lookupMacVendor(mac),
            latencyMs = latencyMs,
            osGuess = OsDetector.guess(ttl, openPorts.map { it.port }),
            isAlive = isAlive,
            openPorts = openPorts
        )
    }

    /**
     * Iterasi daftar IP secara paralel (chunk).
     * [scanOne] mengembalikan null jika host tidak ditemukan; hasilnya diteruskan
     * ke [onResult] secara berurutan agar progress tetap realtime per-IP.
     */
    suspend fun scanIps(
        ips: List<String>,
        hostConcurrency: Int,
        scanOne: suspend (String) -> HostInfo?,
        onResult: suspend (ip: String, host: HostInfo?) -> Unit
    ) {
        ips.chunked(hostConcurrency).forEach { chunk ->
            coroutineScope {
                val deferreds = chunk.map { ip -> async { ip to scanOne(ip) } }
                deferreds.forEach { deferred ->
                    val (ip, host) = deferred.await()
                    onResult(ip, host)
                }
            }
        }
    }

    /**
     * Loop scan antar subnet yang dipakai semua scanner: progres per subnet,
     * pause antar subnet, dan hitung total IP + temuan secara terpusat.
     * [label] dipakai di teks progress (mis. "Port scan", "Discover").
     * Mendukung resume dari posisi terakhir (ScanCheckpoint) dan retry sekali
     * untuk host yang tidak merespons agar risiko skip mengecil.
     */
    suspend fun scanSubnets(
        subnets: List<NetworkUtils.SubnetTarget>,
        speed: ScanSpeed,
        label: String,
        scanOne: suspend (String) -> HostInfo?,
        onEvent: suspend (ScanEvent) -> Unit
    ) {
        val total = subnets.sumOf { (it.hostEnd - it.hostStart + 1).toLong() }
        val isWide = subnets.size > 4
        val batchSize = if (isWide) speed.hostWide else speed.hostLocal
        val resume = ScanCheckpoint.takeIfResume()
        val preDone = if (resume != null) {
            subnets.take(resume.first).sumOf { (it.hostEnd - it.hostStart + 1).toLong() } + resume.second
        } else 0L
        var completed = preDone
        var found = 0
        val startMs = System.currentTimeMillis()

        val intro = if (resume != null) "Lanjut $label dari posisi terakhir"
        else "$label ${subnets.size} subnet — ${subnets.first().prefix} … ${subnets.last().prefix} (${total} IP)"
        onEvent(ScanEvent.Progress(intro, completed.toInt(), total.toInt()))

        val totalSubnets = subnets.size
        val missed = mutableListOf<String>()
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            if (resume != null && subnetIndex < resume.first) {
                // Subnet yang sudah tuntas sebelum resume: cukup dihitung, tidak discan ulang
                return@forEachIndexed
            }
            val startOffset = if (resume != null && subnetIndex == resume.first) resume.second else 0
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"
            var subnetDone = 0

            onEvent(ScanEvent.Progress(
                "$subnetLabel — ${subnet.prefix}.0/24", completed.toInt(), total.toInt(),
                subnetIndex, startOffset))

            val toScan = if (startOffset > 0) ips.drop(startOffset) else ips
            scanIps(toScan, batchSize, scanOne) { ip, host ->
                if (host == null) missed.add(ip)
                else { found++; onEvent(ScanEvent.HostFound(host)) }
                completed++; subnetDone++
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                onEvent(ScanEvent.Progress(
                    "$subnetLabel · $ip · $found ditemukan · ${elapsed}s",
                    completed.toInt(), total.toInt(), subnetIndex, startOffset + subnetDone))
            }
        }

        // Retry sekali host yang tidak merespons (mis. timeout karena paralel padat).
        // Batasi jumlahnya agar scan luas tidak melambat berlebihan.
        if (missed.isNotEmpty() && missed.size <= MAX_RETRY_HOSTS) {
            ScanPause.checkPause()
            onEvent(ScanEvent.Progress("Retry ${missed.size} host yang tidak merespons...",
                completed.toInt(), total.toInt()))
            scanIps(missed, batchSize, scanOne) { ip, host ->
                if (host != null) {
                    found++
                    onEvent(ScanEvent.HostFound(host))
                    onEvent(ScanEvent.Progress(
                        "Retry · $ip ditemukan · $found total", completed.toInt(), total.toInt(),
                        subnetIndex = -1))
                }
            }
        }
    }

    private const val MAX_RETRY_HOSTS = 2000
}
