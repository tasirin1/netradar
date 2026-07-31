package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PingSweep {

    fun scan(target: String): Flow<ScanEvent> = flow {
        val ips = NetworkUtils.autoExpandTarget(target)
        if (ips.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val isWide = ips.size > 1024 || ips.map { it.substringBeforeLast(".") }.distinct().size > 4
        var scanIps = ips.toList()

        if (!isWide && ips.size > 1) {
            emit(ScanEvent.Progress("Discovering live hosts...", 0, ips.size))
            val live = NetworkUtils.discoverLiveHosts(ips)
            if (live.isNotEmpty()) {
                scanIps = ips.filter { it in live }
                if (scanIps.isEmpty()) scanIps = ips.take(10)
            }
        } else if (isWide) {
            emit(ScanEvent.Progress("Wide scan: ${ips.size} IPs in ${ips.map { it.substringBeforeLast(".") }.distinct().size} subnets", 0, ips.size))
        }

        val total = scanIps.size
        var completed = 0
        val arpTable = NetworkUtils.readArpTable()
        val batchSize = if (isWide) 50 else 1

        // ★ Fix: collect results inside coroutineScope, emit OUTSIDE
        scanIps.chunked(batchSize).forEach { batch ->
            val batchResults = coroutineScope {
                batch.map { ip ->
                    async(Dispatchers.IO) {
                        val latency = PingUtil.ping(ip)
                        if (latency != null) {
                            val mac = arpTable[ip]
                            val vendor = NetworkUtils.lookupMacVendor(mac)
                            val hostname = try {
                                kotlinx.coroutines.withTimeout(300) {
                                    java.net.InetAddress.getByName(ip).hostName
                                }.let { if (it != ip) it else null }
                            } catch (_: Exception) { null }
                            HostInfo(ip = ip, hostname = hostname, macAddress = mac,
                                macVendor = vendor, latencyMs = latency, isAlive = true)
                        } else null
                    }
                }.map { it.await() }
            }
            // Emit hasil setelah coroutineScope selesai
            batchResults.forEach { host ->
                completed++
                emit(ScanEvent.Progress(host?.ip ?: "", completed, total))
                if (host != null) emit(ScanEvent.HostFound(host))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PING, target = target)))
    }
}
