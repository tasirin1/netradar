package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class CameraScanner {

    private val cameraPorts = intArrayOf(
        80, 8080, 443, 8443, 554, 8554, 34567, 37777, 37215,
        8899, 9000, 7070, 6666, 87, 85
    )

    fun scan(target: String, speed: ScanSpeed = ScanSpeed.SEDANG): Flow<ScanEvent> = flow {
        val subnets = NetworkUtils.expandTargetSubnets(target)
        if (subnets.isEmpty()) {
            emit(ScanEvent.Error("No IPs to scan"))
            return@flow
        }

        val total = subnets.size * 254L
        val isWide = subnets.size > 4
        val hostConcurrency = if (isWide) speed.hostWide else speed.hostLocal
        val arpTable = NetworkUtils.readArpTable()
        var completed = 0L
        var found = 0
        val startMs = System.currentTimeMillis()

        emit(ScanEvent.Progress(
            "CCTV scan ${subnets.size} subnet — ${subnets.first()} … ${subnets.last()} (${total} IP)",
            0, total.toInt()))

        val totalSubnets = subnets.size
        subnets.forEachIndexed { subnetIndex, subnet ->
            ScanPause.checkPause()
            val ips = NetworkUtils.expandSubnetHosts(subnet)
            val subnetLabel = "Subnet ${subnetIndex + 1}/$totalSubnets"

            emit(ScanEvent.Progress("$subnetLabel — $subnet.0/24", completed.toInt(), total.toInt()))

            // Scan SEMUA IP — tanpa live-host filter agar tidak ada host yang ke-skip
            // (banyak perangkat tidak membalas ICMP tapi portnya terbuka)
            ScanLoop.scanIps(ips, hostConcurrency, scanOne = { ip ->
                val foundServices = scanCameraPorts(ip, speed.timeoutMs)
                if (foundServices.isEmpty()) null else ScanLoop.hostInfo(ip, arpTable, openPorts = foundServices)
            }) { ip, host ->
                completed++
                if (host != null) { found++; emit(ScanEvent.HostFound(host)) }
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                emit(ScanEvent.Progress("$subnetLabel · $ip · $found ditemukan · ${elapsed}s", completed.toInt(), total.toInt()))
            }
        }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.CAMERA, target = target)))
    }

    private suspend fun scanCameraPorts(ip: String, timeoutMs: Int): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            cameraPorts.map { port ->
                async { probeCamera(ip, port, timeoutMs) }
            }.mapNotNull { it.await() }
        }
    }

    private suspend fun probeCamera(ip: String, port: Int, timeoutMs: Int): PortInfo? = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), timeoutMs)
            sock.soTimeout = timeoutMs

            when (port) {
                554, 8554 -> {
                    val req = "OPTIONS rtsp://$ip RTSP/1.0\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val resp = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) resp.append(line).append("\n")
                    sock.close()
                    if (resp.toString().contains("RTSP", ignoreCase = true)) PortInfo(port, "RTSP Camera")
                    else null
                }
                34567 -> PortInfo(port, "Hikvision SDK")
                37777 -> PortInfo(port, "Dahua SDK")
                37215 -> PortInfo(port, "Hikvision Backdoor")
                80, 8080, 443, 8443 -> {
                    val req = "GET / HTTP/1.1\r\nHost: $ip\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val header = StringBuilder()
                    var line: String?
                    for (i in 0 until 25) {
                        line = reader.readLine() ?: break
                        header.append(line).append(" ")
                    }
                    sock.close()
                    val h = header.toString().lowercase()
                    when {
                        h.contains("hikvision") -> PortInfo(port, "Hikvision Web")
                        h.contains("dahua") -> PortInfo(port, "Dahua Web")
                        h.contains("axis") -> PortInfo(port, "Axis Web")
                        h.contains("foscam") -> PortInfo(port, "Foscam Web")
                        h.contains("webcam") || h.contains("camera") -> PortInfo(port, "Camera Web")
                        else -> null
                    }
                }
                8899, 7070 -> PortInfo(port, "Camera Stream")
                9000 -> {
                    try {
                        val xml = """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
  xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
  xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
  <soap:Body>
    <tds:GetDeviceInformation/>
  </soap:Body>
</soap:Envelope>""".trimIndent()
                        val conn = URL("http://$ip:$port/onvif/device_service").openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/soap+xml")
                        conn.doOutput = true
                        conn.connectTimeout = timeoutMs
                        conn.outputStream.write(xml.toByteArray())
                        val resp = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        if (resp.contains("ONVIF", ignoreCase = true) || resp.contains("Device", ignoreCase = true))
                            PortInfo(port, "ONVIF Camera") else null
                    } catch (_: Exception) { null }
                }
                else -> PortInfo(port, "Camera Port")
            }
        } catch (_: Exception) { null }
    }
}
