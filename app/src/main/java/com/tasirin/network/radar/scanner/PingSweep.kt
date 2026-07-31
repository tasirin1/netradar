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
        var found = 0
        val startMs = System.currentTimeMillis()
        val arpTable = NetworkUtils.readArpTable()

        emit(ScanEvent.Progress(
            "Ping ${subnets.size} subnet — ${subnets.first()} … ${subnets.last()} (${total} IP)",
            0, total.toInt()))

        val totalSubnets = subnets.size
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"

            emit(ScanEvent.Progress("$subnetLabel — $subnet.0/24", completed.toInt(), total.toInt()))

            ips.chunked(batchSize).forEach { chunk ->
                coroutineScope {
                    val deferreds = chunk.map { ip ->
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
                                ip to HostInfo(ip = ip, hostname = hostname, macAddress = mac,
                                    macVendor = vendor, latencyMs = latency, isAlive = true)
                            } else ip to null
                        }
                    }
                    deferreds.forEach { deferred ->
                        val (ip, host) = deferred.await()
                        completed++
                        if (host != null) found++
                        val elapsed = (System.currentTimeMillis() - startMs) / 1000
                        emit(ScanEvent.Progress("$subnetLabel · $ip · $found ditemukan · ${elapsed}s", completed.toInt(), total.toInt()))
                        if (host != null) emit(ScanEvent.HostFound(host))
                    }
                }
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PING, target = target)))
    }
}
