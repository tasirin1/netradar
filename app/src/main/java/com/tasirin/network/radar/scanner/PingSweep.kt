package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PingSweep {

    fun scan(target: String): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val batchSize = if (isWide) 50 else 10
        var completed = 0L
        val arpTable = NetworkUtils.readArpTable()

        emit(ScanEvent.Progress("Scanning ${subnets.size} subnet(s), ${total} IP(s)...", 0, total.toInt()))

        subnets.forEach { subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)

            val results = withContext(Dispatchers.IO) {
                ips.chunked(batchSize).map { chunk ->
                    chunk.map { ip ->
                        async {
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
                    }.awaitAll()
                }.flatten()
            }

            results.forEach { host ->
                completed++
                emit(ScanEvent.Progress(host?.ip ?: subnet, completed.toInt(), total.toInt()))
                if (host != null) emit(ScanEvent.HostFound(host))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PING, target = target)))
    }
}
