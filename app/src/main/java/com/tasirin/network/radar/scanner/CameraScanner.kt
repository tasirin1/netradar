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
import java.util.concurrent.Semaphore

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

        val arpTable = NetworkUtils.readArpTable()
        val permits = Semaphore(speed.socketPermits)

        // Scan SEMUA IP — tanpa live-host filter agar tidak ada host yang ke-skip
        ScanLoop.scanSubnets(subnets, speed, "CCTV scan", scanOne = { ip ->
            val foundServices = scanCameraPorts(ip, speed.timeoutMs, permits)
            if (foundServices.isEmpty()) null else ScanLoop.hostInfo(ip, arpTable, openPorts = foundServices)
        }) { ev -> emit(ev) }

        emit(ScanEvent.Complete(ScanResult(type = ScanType.CAMERA, target = target)))
    }

    private suspend fun scanCameraPorts(ip: String, timeoutMs: Int, permits: Semaphore): List<PortInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            cameraPorts.map { port ->
                async { probeCamera(ip, port, timeoutMs, permits) }
            }.mapNotNull { it.await() }
        }
    }

    private suspend fun probeCamera(ip: String, port: Int, timeoutMs: Int, permits: Semaphore): PortInfo? = withContext(Dispatchers.IO) {
        permits.acquire()
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(ip, port), timeoutMs)
            sock.soTimeout = timeoutMs

            val result: PortInfo? = when (port) {
                554, 8554 -> {
                    val req = "OPTIONS rtsp://$ip RTSP/1.0\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val resp = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) resp.append(line).append("\n")
                    if (resp.toString().contains("RTSP", ignoreCase = true)) PortInfo(port, "RTSP Camera")
                    else null
                }
                34567 -> PortInfo(port, "Hikvision SDK")
                37777 -> PortInfo(port, "Dahua SDK")
                37215 -> PortInfo(port, "Hikvision Backdoor")
                80, 8080, 443, 8443 -> {
                    val req = "GET / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().write(req.toByteArray())
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "ISO-8859-1"))
                    val header = StringBuilder()
                    var line: String?
                    for (i in 0 until 25) {
                        line = reader.readLine() ?: break
                        header.append(line).append(" ")
                    }
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
                    // Socket hanya untuk cek port terbuka; ONVIF pakai koneksi HTTP terpisah
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
                        try {
                            conn.outputStream.write(xml.toByteArray())
                            val resp = conn.inputStream.bufferedReader().readText()
                            if (resp.contains("ONVIF", ignoreCase = true) || resp.contains("Device", ignoreCase = true))
                                PortInfo(port, "ONVIF Camera") else null
                        } finally {
                            conn.disconnect()
                        }
                    } catch (_: Exception) { null }
                }
                else -> PortInfo(port, "Camera Port")
            }
            result
        } catch (_: Exception) { null }
        finally {
            try { sock.close() } catch (_: Exception) {}
            permits.release()
        }
    }
}
