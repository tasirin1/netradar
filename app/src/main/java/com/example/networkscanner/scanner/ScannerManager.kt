package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ScannerManager {

    private val portScanner = PortScanner()
    private val cameraScanner = CameraScanner()
    private val routerScanner = RouterScanner()
    private val urlPathScanner = UrlPathScanner()
    private val discoverScanner = DiscoverScanner()
    private val pingSweep = PingSweep()

    private var currentJob: Job? = null

    fun scan(type: ScanType, target: String): Flow<ScanEvent> = channelFlow {
        // Cancel any previous scan
        currentJob?.cancel()
        currentJob = null

        val job = CoroutineScope(Dispatchers.IO + currentJob ?: Job()).launch {
            val flow = when (type) {
                ScanType.PORT_SCAN -> portScanner.scan(target)
                ScanType.CAMERA -> cameraScanner.scan(target)
                ScanType.ROUTER -> routerScanner.scan(target)
                ScanType.URL_PATH -> urlPathScanner.scan(target)
                ScanType.DISCOVER -> discoverScanner.scan(target)
                ScanType.PING -> pingSweep.scan(target)
            }
            flow.collect { event ->
                send(event)
            }
        }
        currentJob = job
        job.join()
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
    }

    fun isRunning(): Boolean = currentJob?.isActive == true
}
