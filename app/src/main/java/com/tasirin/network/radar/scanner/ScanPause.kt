package com.tasirin.network.radar.scanner

import kotlinx.coroutines.delay

/**
 * Global pause control shared by all scanners.
 * Scanners call checkPause() between batches; while paused, they wait.
 */
object ScanPause {
    @Volatile
    var paused = false

    suspend fun checkPause() {
        while (paused) {
            delay(250)
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
}
