package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.OsDetector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        val hostname = hostname(ip) ?: MdnsNameResolver.nameFor(ip)
        val vendor = NetworkUtils.lookupMacVendor(mac)
        return HostInfo(
            ip = ip,
            hostname = hostname,
            label = hostname ?: vendor,
            macAddress = mac,
            macVendor = vendor,
            latencyMs = latencyMs,
            osGuess = OsDetector.guess(ttl, openPorts.map { it.port }),
            isAlive = isAlive,
            openPorts = openPorts
        )
    }

    /**
     * Iterasi daftar IP secara paralel (chunk).
     * [scanOne] mengembalikan null jika host tidak ditemukan; hasilnya diteruskan
     * ke [onResult] sesuai urutan selesai agar progress tetap realtime per-IP.
     */
    suspend fun scanIps(
        ips: List<String>,
        hostConcurrency: Int,
        scanOne: suspend (String) -> HostInfo?,
        onResult: suspend (ip: String, host: HostInfo?) -> Unit
    ) {
        ips.chunked(hostConcurrency).forEach { chunk ->
            coroutineScope {
                // Lapor sesuai urutan selesai (bukan urutan IP) agar satu host
                // lambat tidak menahan laporan host cepat di chunk yang sama.
                // Penghitung checkpoint berbasis jumlah selesai sehingga aman.
                val channel = Channel<Pair<String, HostInfo?>>(chunk.size)
                chunk.forEach { ip ->
                    launch { channel.send(ip to scanOne(ip)) }
                }
                repeat(chunk.size) {
                    val (ip, host) = channel.receive()
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
        onEvent(ScanEvent.Progress(intro, completed.coerceAtMost(total).toInt(), total.toInt()))

        val totalSubnets = subnets.size
        // Daftar retry dibatasi: scan luas yang mostly-kosong tidak boleh menumpuk
        // jutaan string IP di memori (OOM). Kelebihan dihitung tapi tidak disimpan.
        val missed = mutableListOf<String>()
        var missedCount = 0
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            if (resume != null && subnetIndex < resume.first) {
                // Subnet yang sudah tuntas sebelum resume: cukup dihitung, tidak discan ulang
                return@forEachIndexed
            }
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val startOffset = if (resume != null && subnetIndex == resume.first) resume.second else 0
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"
            var subnetDone = 0

            onEvent(ScanEvent.Progress(
                "$subnetLabel — ${subnet.prefix}.0/24", completed.coerceAtMost(total).toInt(), total.toInt(),
                subnetIndex, startOffset))

            // Checkpoint basi (mis. target beda ukuran) dijepit agar tidak crash
            // IndexOutOfBoundsException saat resume.
            val safeOffset = startOffset.coerceIn(0, ips.size)
            val toScan = if (safeOffset >= ips.size) emptyList() else ips.subList(safeOffset, ips.size)
            scanIps(toScan, batchSize, scanOne) { ip, host ->
                if (host == null) {
                    missedCount++
                    if (missed.size < MAX_RETRY_HOSTS) missed.add(ip)
                } else { found++; onEvent(ScanEvent.HostFound(host)) }
                completed++; subnetDone++
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                onEvent(ScanEvent.Progress(
                    "$subnetLabel · $ip · $found ditemukan · ${elapsed}s",
                    completed.coerceAtMost(total).toInt(), total.toInt(), subnetIndex, startOffset + subnetDone))
            }
        }

        // Retry sekali host yang tidak merespons (mis. timeout karena paralel padat).
        // Retry TIDAK menambah completed (host sudah dihitung di loop utama) agar
        // progress tidak melebihi 100%. Dilewati bila host hilang melebihi batas.
        if (missedCount in 1..MAX_RETRY_HOSTS) {
            ScanPause.checkPause()
            onEvent(ScanEvent.Progress("Retry ${missed.size} host yang tidak merespons...",
                completed.coerceAtMost(total).toInt(), total.toInt()))
            var retried = 0
            scanIps(missed, batchSize, scanOne) { ip, host ->
                retried++
                if (host != null) {
                    found++
                    onEvent(ScanEvent.HostFound(host))
                }
                onEvent(ScanEvent.Progress(
                    "Retry ${retried}/${missed.size} · $ip" + if (host != null) " · $found total" else "",
                    completed.coerceAtMost(total).toInt(), total.toInt(),
                    subnetIndex = -1))
            }
        }
    }

    private const val MAX_RETRY_HOSTS = 2000
}
