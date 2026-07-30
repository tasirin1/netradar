package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import com.example.networkscanner.util.DebugLogger
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
        DebugLogger.log("MGR", "Scan requested: ${type.label} @ $target")
        currentJob?.cancel()
        currentJob = null

        // Use channelFlow's own scope with IO dispatcher
        val scanJob = launch(Dispatchers.IO) {
            val scannerFlow = when (type) {
                ScanType.PORT_SCAN -> portScanner.scan(target)
                ScanType.CAMERA -> cameraScanner.scan(target)
                ScanType.ROUTER -> routerScanner.scan(target)
                ScanType.URL_PATH -> urlPathScanner.scan(target)
                ScanType.DISCOVER -> discoverScanner.scan(target)
                ScanType.PING -> pingSweep.scan(target)
            }
            try {
                scannerFlow.collect { event ->
                    // Send to channelFlow's channel
                    send(event)
                }
            } catch (e: CancellationException) {
                // Scan was cancelled, rethrow
                throw e
            } catch (e: Exception) {
                DebugLogger.log("MGR", "Error: ${e.message}")
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
            DebugLogger.log("MGR", "Scan complete")
            currentJob = null
        }
    }

    fun stop() {
        DebugLogger.log("MGR", "Stop requested")
        currentJob?.cancel()
        currentJob = null
    }

    fun isRunning(): Boolean = currentJob?.isActive == true
}
