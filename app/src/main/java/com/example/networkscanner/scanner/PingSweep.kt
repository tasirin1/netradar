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

        // Like v1.0: pre-filter with ARP discovery for local subnets
        var scanIps = ips.toList()
        val localIp = NetworkUtils.getLocalIp()
        val isLocal = localIp != null && ips.any { it.startsWith(localIp.substringBeforeLast(".")) }

        if (isLocal && ips.size > 1) {
            emit(ScanEvent.Progress("Discovering live hosts...", 0, ips.size))
            val subnet = ips.first().substringBeforeLast(".") + "."
            val live = NetworkUtils.arpScan(subnet)
            if (live.isNotEmpty()) {
                scanIps = ips.filter { it in live }
            }
        }

        val total = scanIps.size
        var completed = 0
        val arpTable = NetworkUtils.readArpTable()

        for (ip in scanIps) {
            emit(ScanEvent.Progress(ip, completed, total))

            val latency = PingUtil.ping(ip)
            if (latency != null) {
                val mac = arpTable[ip]
                val vendor = NetworkUtils.lookupMacVendor(mac)

                val hostname = try {
                    val hn = java.net.InetAddress.getByName(ip).hostName
                    if (hn != ip) hn else null
                } catch (_: Exception) { null }

                val host = HostInfo(
                    ip = ip,
                    hostname = hostname,
                    macAddress = mac,
                    macVendor = vendor,
                    latencyMs = latency,
                    isAlive = true
                )
                emit(ScanEvent.HostFound(host))
            }
            completed++
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PING, target = target)))
    }
}
