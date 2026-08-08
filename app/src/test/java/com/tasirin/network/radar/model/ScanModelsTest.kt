package com.tasirin.network.radar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifikasi deteksi jenis perangkat dari port terbuka dan logika diff antar scan. */
class ScanModelsTest {

    private fun host(ip: String, ports: List<Int>) = HostInfo(ip = ip, openPorts = ports.map { PortInfo(it) })

    @Test
    fun `RTSP menandai kamera`() {
        val kinds = host("192.168.0.10", listOf(80, 554)).deviceKinds()
        assertTrue(DeviceKind.CAMERA in kinds)
    }

    @Test
    fun `port Hikvision menandai kamera`() {
        assertTrue(DeviceKind.CAMERA in host("192.168.0.11", listOf(34567)).deviceKinds())
        assertTrue(DeviceKind.CAMERA in host("192.168.0.12", listOf(37215)).deviceKinds())
    }

    @Test
    fun `winbox dan TR-069 menandai router`() {
        assertTrue(DeviceKind.ROUTER in host("192.168.0.1", listOf(8291)).deviceKinds())
        assertTrue(DeviceKind.ROUTER in host("192.168.0.2", listOf(7547)).deviceKinds())
    }

    @Test
    fun `port berbagi file menandai share`() {
        assertTrue(DeviceKind.SHARE in host("192.168.0.20", listOf(445)).deviceKinds())
        assertTrue(DeviceKind.SHARE in host("192.168.0.21", listOf(139)).deviceKinds())
        assertTrue(DeviceKind.SHARE in host("192.168.0.22", listOf(21)).deviceKinds())
    }

    @Test
    fun `port cetak menandai printer`() {
        assertTrue(DeviceKind.PRINTER in host("192.168.0.30", listOf(631)).deviceKinds())
        assertTrue(DeviceKind.PRINTER in host("192.168.0.31", listOf(9100)).deviceKinds())
    }

    @Test
    fun `AFP dan iSCSI menandai NAS`() {
        assertTrue(DeviceKind.NAS in host("192.168.0.40", listOf(548)).deviceKinds())
        assertTrue(DeviceKind.NAS in host("192.168.0.41", listOf(3260)).deviceKinds())
    }

    @Test
    fun `port DLNA menandai TV`() {
        assertTrue(DeviceKind.TV in host("192.168.0.50", listOf(8060)).deviceKinds())
        assertTrue(DeviceKind.TV in host("192.168.0.51", listOf(55000)).deviceKinds())
    }

    @Test
    fun `MQTT menandai IoT`() {
        assertTrue(DeviceKind.IOT in host("192.168.0.60", listOf(1883)).deviceKinds())
        assertTrue(DeviceKind.IOT in host("192.168.0.61", listOf(5683)).deviceKinds())
    }

    @Test
    fun `ADB menandai hp`() {
        assertTrue(DeviceKind.PHONE in host("192.168.0.70", listOf(5555)).deviceKinds())
    }

    @Test
    fun `host tanpa port tidak berjenis apa pun`() {
        assertEquals(emptySet<DeviceKind>(), host("192.168.0.99", emptyList()).deviceKinds())
    }

    @Test
    fun `diff menangkap host baru hilang dan berubah`() {
        val previous = mapOf("10.0.0.1" to listOf(80), "10.0.0.2" to listOf(22), "10.0.0.3" to listOf(443))
        val current = mapOf("10.0.0.1" to listOf(80), "10.0.0.2" to listOf(22, 443), "10.0.0.4" to listOf(8080))
        val diff = ScanDiff.compute(current, previous) { HostInfo(it) }
        assertEquals(listOf("10.0.0.4"), diff.added.map { it.ip })
        assertEquals(listOf("10.0.0.3"), diff.removed.map { it.ip })
        assertEquals(listOf("10.0.0.2"), diff.changed.map { it.ip })
    }

    @Test
    fun `diff tidak menandai perubahan urutan port sebagai berubah`() {
        val previous = mapOf("10.0.0.1" to listOf(80, 443))
        val current = mapOf("10.0.0.1" to listOf(443, 80))
        val diff = ScanDiff.compute(current, previous) { HostInfo(it) }
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.changed.isEmpty())
    }

    @Test
    fun `diff kosong saat hasil sama`() {
        val scan = mapOf("10.0.0.1" to listOf(80), "10.0.0.2" to listOf(22))
        val diff = ScanDiff.compute(scan, scan) { HostInfo(it) }
        assertTrue(diff.added.isEmpty() && diff.removed.isEmpty() && diff.changed.isEmpty())
    }

    @Test
    fun `diff pertama kali semua host masuk added`() {
        val current = mapOf("10.0.0.1" to listOf(80))
        val diff = ScanDiff.compute(current, emptyMap()) { HostInfo(it) }
        assertEquals(listOf("10.0.0.1"), diff.added.map { it.ip })
    }
}
