package com.tasirin.network.radar.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/** Target traceroute: port dibuang, literal IPv6 dibiarkan utuh. */
class TracerouteTargetTest {

    private val scanner = TracerouteScanner()

    @Test
    fun `host polos utuh`() {
        assertEquals("192.168.1.1", scanner.stripPort("192.168.1.1"))
    }

    @Test
    fun `host port dibuang`() {
        assertEquals("192.168.1.1", scanner.stripPort("192.168.1.1:8080"))
    }

    @Test
    fun `ipv6 literal utuh`() {
        assertEquals("2001:db8::1", scanner.stripPort("2001:db8::1"))
    }

    @Test
    fun `ipv6 kurung plus port`() {
        assertEquals("::1", scanner.stripPort("[::1]:8080"))
    }
}
