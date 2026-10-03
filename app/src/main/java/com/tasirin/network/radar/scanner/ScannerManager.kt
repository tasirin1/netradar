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
    private val udpScanner = UdpScanner()
    private val tracerouteScanner = TracerouteScanner()

    /** Kunci untuk assignment currentJob agar scan/stop yang bersamaan tidak saling menimpa. */
    private val jobLock = Any()

    @Volatile
    private var currentJob: Job? = null

    fun scan(
        type: ScanType,
        target: String,
        speed: ScanSpeed = ScanSpeed.SEDANG,
        customPorts: String = ""
    ): Flow<ScanEvent> = channelFlow {
        // Ambil dan batalkan job lama di bawah kunci agar tidak ada dua scan yang
        // menganggap dirinya pemilik currentJob secara bersamaan.
        val oldJob: Job? = synchronized(jobLock) {
            val previous = currentJob
            currentJob = null
            previous
        }
        oldJob?.cancel()
        ScanPause.resume() // scan baru mulai dalam keadaan tidak paused

        val scanJob = launch(Dispatchers.IO) {
            try {
                // Kumpulkan nama perangkat mDNS/SSDP sekali per scan (best-effort).
                withTimeoutOrNull(2500) { MdnsNameResolver.refreshIfStale() }
                val scannerFlow = when (type) {
                    ScanType.PORT_SCAN -> portScanner.scan(target, speed, customPorts)
                    ScanType.CAMERA -> cameraScanner.scan(target, speed)
                    ScanType.ROUTER -> routerScanner.scan(target, speed)
                    ScanType.URL_PATH -> urlPathScanner.scan(target)
                    ScanType.DISCOVER -> discoverScanner.scan(target, speed)
                    ScanType.PING -> pingSweep.scan(target, speed)
                    ScanType.UDP -> udpScanner.scan(target, speed)
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
        synchronized(jobLock) { currentJob = scanJob }

        // Tunggu job lama selesai dibatalkan agar tidak ada dua scan paralel
        oldJob?.join()

        try {
            scanJob.join()
        } catch (e: CancellationException) {
            scanJob.cancel()
            throw e
        } finally {
            // Hanya nol-kan bila masih milik scan ini: stop()/scan baru yang
            // datang belakangan tidak boleh dibuat yatim (tak bisa di-cancel).
            synchronized(jobLock) { if (currentJob === scanJob) currentJob = null }
            ScanPause.resume()
        }
    }

    fun pause() { ScanPause.pause() }
    fun resume() { ScanPause.resume() }

    /** Batalkan scan berjalan tanpa membuat scan baru yang sedang start jadi yatim. */
    fun stop() {
        val job: Job? = synchronized(jobLock) {
            val running = currentJob
            currentJob = null
            running
        }
        job?.cancel()
        ScanPause.resume()
    }
}
