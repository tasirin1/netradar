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
    private val tracerouteScanner = TracerouteScanner()

    @Volatile
    private var currentJob: Job? = null

    fun scan(
        type: ScanType,
        target: String,
        speed: ScanSpeed = ScanSpeed.SEDANG
    ): Flow<ScanEvent> = channelFlow {
        currentJob?.cancel()
        currentJob = null
        ScanPause.resume() // scan baru mulai dalam keadaan tidak paused

        val scanJob = launch(Dispatchers.IO) {
            try {
                val scannerFlow = when (type) {
                    ScanType.PORT_SCAN -> portScanner.scan(target, speed)
                    ScanType.CAMERA -> cameraScanner.scan(target, speed)
                    ScanType.ROUTER -> routerScanner.scan(target, speed)
                    ScanType.URL_PATH -> urlPathScanner.scan(target)
                    ScanType.DISCOVER -> discoverScanner.scan(target, speed)
                    ScanType.PING -> pingSweep.scan(target, speed)
                    ScanType.TRACE -> tracerouteScanner.scan(target)
                    ScanType.MONITOR -> emptyFlow()
                }
                scannerFlow.collect { event -> send(event) }
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
            ScanPause.resume()
        }
    }

    fun pause() { ScanPause.pause() }
    fun resume() { ScanPause.resume() }
    fun stop() { currentJob?.cancel(); currentJob = null; ScanPause.resume() }
}
