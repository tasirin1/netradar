package com.tasirin.network.radar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifikasi tebakan OS/jenis perangkat dari TTL ping dan port terbuka. */
class OsDetectorTest {

    @Test
    fun `TTL 64 dianggap linux atau android`() {
        assertEquals("Linux/Android", OsDetector.guess(64, emptyList()))
    }

    @Test
    fun `TTL 128 dianggap windows`() {
        assertEquals("Windows", OsDetector.guess(128, emptyList()))
    }

    @Test
    fun `TTL 255 dianggap perangkat jaringan`() {
        assertEquals("Network Device", OsDetector.guess(255, emptyList()))
    }

    @Test
    fun `TTL rendah dianggap unix`() {
        assertEquals("Unix", OsDetector.guess(30, emptyList()))
    }

    @Test
    fun `port router menang di atas tebakan ttl`() {
        assertEquals("Router/Network", OsDetector.guess(64, listOf(8291)))
        assertEquals("Router/Network", OsDetector.guess(128, listOf(7547)))
    }

    @Test
    fun `port kamera menang di atas tebakan ttl`() {
        assertEquals("Camera Device", OsDetector.guess(128, listOf(554)))
        assertEquals("Camera Device", OsDetector.guess(255, listOf(34567)))
    }

    @Test
    fun `ttl null tanpa port tidak ada tebakan`() {
        assertNull(OsDetector.guess(null, emptyList()))
    }

    @Test
    fun `ttl null dengan port tetap terdeteksi`() {
        assertEquals("Router/Network", OsDetector.guess(null, listOf(23)))
    }
}
