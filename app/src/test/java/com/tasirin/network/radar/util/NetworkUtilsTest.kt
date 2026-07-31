package com.tasirin.network.radar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifikasi logika ekspansi target: 192.16 harus lanjut sampai 192.255.255, bukan berhenti di 192.16.0. */
class NetworkUtilsTest {

    @Test
    fun `dua oktet tanpa titik lanjut sampai 255`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.16")
        assertEquals(61440, subnets.size)               // (255-16+1) * 256
        assertEquals("192.16.0", subnets.first())
        assertEquals("192.16.255", subnets[255])
        assertEquals("192.17.0", subnets[256])          // lanjut ke 192.17
        assertEquals("192.255.255", subnets.last())
    }

    @Test
    fun `dua oktet dengan titik akhir sama hasilnya`() {
        assertEquals(NetworkUtils.expandTargetSubnets("192.16"),
            NetworkUtils.expandTargetSubnets("192.16."))
    }

    @Test
    fun `satu oktet lanjut 0 sampai 255`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.")
        assertEquals(65536, subnets.size)
        assertEquals("192.0.0", subnets.first())
        assertEquals("192.255.255", subnets.last())
    }

    @Test
    fun `tiga oktet jadi satu subnet`() {
        assertEquals(listOf("192.168.5"), NetworkUtils.expandTargetSubnets("192.168.5"))
        assertEquals(listOf("192.168.5"), NetworkUtils.expandTargetSubnets("192.168.5."))
    }

    @Test
    fun `IP penuh jadi satu subnet`() {
        assertEquals(listOf("192.168.5"), NetworkUtils.expandTargetSubnets("192.168.5.1"))
    }

    @Test
    fun `range IP jadi satu subnet`() {
        assertEquals(listOf("192.168.0"), NetworkUtils.expandTargetSubnets("192.168.0.1-100"))
    }

    @Test
    fun `CIDR dipecah ke subnet 24`() {
        val subnets = NetworkUtils.expandTargetSubnets("10.5.0.0/16")
        assertEquals(256, subnets.size)
        assertEquals("10.5.0", subnets.first())
        assertEquals("10.5.255", subnets.last())
        assertEquals(listOf("192.168.1"), NetworkUtils.expandTargetSubnets("192.168.1.0/24"))
    }

    @Test
    fun `oktet invalid tidak hang`() {
        assertTrue(NetworkUtils.expandTargetSubnets("300.1").isEmpty())
        assertTrue(NetworkUtils.expandTargetSubnets("").isEmpty())
    }

    @Test
    fun `domain diresolve ke satu subnet`() {
        assertEquals(listOf("127.0.0"), NetworkUtils.expandTargetSubnets("localhost"))
    }
}
