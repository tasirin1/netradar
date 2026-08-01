package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.HostInfo
import com.tasirin.network.radar.model.PortInfo
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
        return HostInfo(
            ip = ip,
            hostname = hostname(ip),
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
}
