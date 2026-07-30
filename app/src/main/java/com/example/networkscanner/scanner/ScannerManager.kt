package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ScannerManager {

    private val portScanner = PortScanner()
    private val cameraScanner = CameraScanner()
    private val routerScanner = RouterScanner()
    private val urlPathScanner = UrlPathScanner()
    private val discoverScanner = DiscoverScanner()
    private val pingSweep = PingSweep()

    @Volatile
    private var currentJob: Job? = null

    fun scan(type: ScanType, target: String): Flow<ScanEvent> = channelFlow {
        currentJob?.cancel()
        currentJob = null

        val scanJob = launch(Dispatchers.IO) {
            val scannerFlow = when (type) {
                ScanType.PORT_SCAN -> portScanner.scan(target)
                ScanType.CAMERA -> cameraScanner.scan(target)
                ScanType.ROUTER -> routerScanner.scan(target)
                ScanType.URL_PATH -> urlPathScanner.scan(target)
                ScanType.DISCOVER -> discoverScanner.scan(target)
                ScanType.PING -> pingSweep.scan(target)
                ScanType.MONITOR -> emptyFlow()
            }
            try {
                scannerFlow.collect { event ->
                    send(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(ScanEvent.Error(e.message ?: "Scan error"))
            }
        }
        currentJob = scanJob

        try {
            scanJob.join()
        } catch (e: CancellationException) {
            scanJob.cancel()
            throw e
        } finally {
            currentJob = null
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
    }

    fun isRunning(): Boolean = currentJob?.isActive == true
}
