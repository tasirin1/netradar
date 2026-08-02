package com.tasirin.network.radar.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Bunyi umpan balik saat scan menemukan perangkat/port (sonifikasi sederhana).
 * Nada disesuaikan dengan service port; di-throttle 250ms agar tidak berdengung.
 */
object SoundFeedback {

    private var toneGenerator: ToneGenerator? = null
    private var lastPlayAt = 0L

    @Synchronized
    fun playForPort(port: Int) {
        val now = System.currentTimeMillis()
        if (now - lastPlayAt < 250) return
        lastPlayAt = now
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            }
            val tone = when (port) {
                80, 443, 8080, 8443, 8000, 8888, 5000, 3000 -> ToneGenerator.TONE_PROP_BEEP
                22, 23, 3389, 5900 -> ToneGenerator.TONE_PROP_BEEP2
                445, 139, 2049, 21 -> ToneGenerator.TONE_PROP_ACK
                554, 8554, 34567, 37777, 37215, 8899 -> ToneGenerator.TONE_CDMA_PIP
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            toneGenerator?.startTone(tone, 60)
        } catch (_: Exception) { }
    }
}
