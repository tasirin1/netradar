package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import com.example.networkscanner.util.MacVendorLookup
import com.example.networkscanner.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PingSweep {

    fun scan(target: String): Flow<ScanEvent> = flow {
        val ips = resolveIps(target)
        val total = ips.size
        var completed = 0
        val arpTable = MacVendorLookup.readArpTable()

        for (ip in ips) {
emit(ScanEvent.Progress(ip, completed, total))

            val latency = PingUtil.ping(ip)
            if (latency != null) {
                val mac = arpTable[ip]
                val vendor = MacVendorLookup.lookup(mac)

                // Try to get hostname
                val hostname = try {
                    java.net.InetAddress.getByName(ip).hostName
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
            delay(20)
        }

        emit(ScanEvent.Complete(ScanResult(
            type = ScanType.PING, target = target
        )))
    }

    private fun resolveIpsOrig(input: String): List<String> {
    private fun resolveIps(input: String): List<String> = com.example.networkscanner.util.NetworkUtils.autoExpandTarget(input)
        return com.example.networkscanner.util.NetworkUtils.resolveTarget(input)
    }
}
