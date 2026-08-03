package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import com.tasirin.network.radar.util.PingUtil
import kotlinx.coroutines.flow.*

class PingSweep {

    fun scan(target: String, speed: ScanSpeed = ScanSpeed.SEDANG): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val arpTable = NetworkUtils.readArpTable()
        ScanLoop.scanSubnets(subnets, speed, "Ping", scanOne = { ip ->
            PingUtil.pingProbe(ip)?.let { probe ->
                ScanLoop.hostInfo(ip, arpTable, latencyMs = probe.latencyMs, ttl = probe.ttl)
            }
        }) { ev -> emit(ev) }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.PING, target = target)))
    }
}
