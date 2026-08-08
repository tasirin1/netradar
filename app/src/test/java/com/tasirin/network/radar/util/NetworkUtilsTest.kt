package com.tasirin.network.radar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifikasi logika ekspansi target: input numerik lanjut dari titik yang diketik sampai 255. */
class NetworkUtilsTest {

    @Test
    fun `dua oktet tanpa titik lanjut sampai 255`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.16")
        assertEquals(61440, subnets.size)               // (255-16+1) * 256
        assertEquals("192.16.0", subnets.first().prefix)
        assertEquals("192.16.255", subnets[255].prefix)
        assertEquals("192.17.0", subnets[256].prefix)   // lanjut ke 192.17
        assertEquals("192.255.255", subnets.last().prefix)
    }

    @Test
    fun `dua oktet dengan titik akhir sama hasilnya`() {
        assertEquals(NetworkUtils.expandTargetSubnets("192.16").map { it.prefix },
            NetworkUtils.expandTargetSubnets("192.16.").map { it.prefix })
    }

    @Test
    fun `satu oktet lanjut 0 sampai 255`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.")
        assertEquals(65536, subnets.size)
        assertEquals("192.0.0", subnets.first().prefix)
        assertEquals("192.255.255", subnets.last().prefix)
    }

    @Test
    fun `tiga oktet lanjut sampai 255`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.5")
        assertEquals(251, subnets.size)                  // (255-5+1) subnet
        assertEquals("192.168.5", subnets.first().prefix)
        assertEquals("192.168.6", subnets[1].prefix)     // lanjut ke 192.168.6
        assertEquals("192.168.255", subnets.last().prefix)
        assertEquals(NetworkUtils.expandTargetSubnets("192.168.5.").map { it.prefix },
            subnets.map { it.prefix })
    }

    @Test
    fun `IP penuh lanjut ke subnet berikutnya`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.15.1")
        assertEquals(241, subnets.size)                  // (255-15+1) subnet
        assertEquals("192.168.15", subnets.first().prefix)
        assertEquals(1, subnets.first().hostStart)       // mulai dari host .1
        assertEquals(254, subnets.first().hostEnd)
        assertEquals("192.168.16", subnets[1].prefix)    // lanjut ke 192.168.16.x
        assertEquals("192.168.255", subnets.last().prefix)
    }

    @Test
    fun `IP penuh mulai dari host tertentu`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.15.50")
        assertEquals(50, subnets.first().hostStart)
        assertEquals(254, subnets.first().hostEnd)
    }

    @Test
    fun `range host dalam satu subnet`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.0.1-100")
        assertEquals(listOf(NetworkUtils.SubnetTarget("192.168.0", hostStart = 1, hostEnd = 100)),
            subnets)
    }

    @Test
    fun `range IP penuh lintas subnet`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.15.1-192.168.16.1")
        assertEquals(listOf(
            NetworkUtils.SubnetTarget("192.168.15", hostStart = 1, hostEnd = 254),
            NetworkUtils.SubnetTarget("192.168.16", hostStart = 1, hostEnd = 1)
        ), subnets)
    }

    @Test
    fun `range IP penuh batas host tengah`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.15.200-192.168.16.5")
        assertEquals("192.168.15", subnets[0].prefix)
        assertEquals(200, subnets[0].hostStart)
        assertEquals(254, subnets[0].hostEnd)
        assertEquals("192.168.16", subnets[1].prefix)
        assertEquals(1, subnets[1].hostStart)
        assertEquals(5, subnets[1].hostEnd)
    }

    @Test
    fun `CIDR dipecah ke subnet 24`() {
        val subnets = NetworkUtils.expandTargetSubnets("10.5.0.0/16")
        assertEquals(256, subnets.size)
        assertEquals("10.5.0", subnets.first().prefix)
        assertEquals("10.5.255", subnets.last().prefix)
        assertEquals(listOf(NetworkUtils.SubnetTarget("192.168.1")),
            NetworkUtils.expandTargetSubnets("192.168.1.0/24"))
    }

    @Test
    fun `oktet invalid tidak hang`() {
        assertTrue(NetworkUtils.expandTargetSubnets("300.1").isEmpty())
        assertTrue(NetworkUtils.expandTargetSubnets("").isEmpty())
    }

    @Test
    fun `domain diresolve ke satu subnet`() {
        assertEquals(listOf(NetworkUtils.SubnetTarget("127.0.0")),
            NetworkUtils.expandTargetSubnets("localhost"))
    }

    @Test
    fun `CIDR /23 dipecah menjadi dua subnet`() {
        val subnets = NetworkUtils.expandTargetSubnets("192.168.1.0/23")
        assertEquals(listOf("192.168.0", "192.168.1"), subnets.map { it.prefix })
    }

    @Test
    fun `CIDR terlalu lebar ditolak`() {
        assertTrue(NetworkUtils.expandTargetSubnets("10.0.0.0/7").isEmpty())
    }

    @Test
    fun `CIDR /8 berukuran batas maksimal`() {
        val subnets = NetworkUtils.expandTargetSubnets("10.0.0.0/8")
        assertEquals(NetworkUtils.MAX_SUBNETS, subnets.size)
        assertEquals("10.0.0", subnets.first().prefix)
        assertEquals("10.255.255", subnets.last().prefix)
    }
}
