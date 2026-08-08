package com.tasirin.network.radar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifikasi daftar port umum default dan tingkat risikonya. */
class PortRangeParserTest {

    @Test
    fun `daftar port default berisi port penting`() {
        val ports = PortRangeParser.defaultPorts.toSet()
        for (port in listOf(80, 443, 22, 23, 21, 3389, 3306, 445, 554, 5555, 2375, 8291)) {
            assertTrue("port $port harus ada di daftar default", port in ports)
        }
    }

    @Test
    fun `daftar port default cukup banyak untuk scan penuh`() {
        assertTrue(PortRangeParser.defaultPorts.size >= ScanSpeed.SANGAT_STABIL.portCount)
        assertTrue(PortRangeParser.defaultPorts.size >= 60)
    }

    @Test
    fun `tidak ada port duplikat`() {
        val ports = PortRangeParser.defaultPorts.toList()
        assertEquals(ports.size, ports.toSet().size)
    }

    @Test
    fun `semua port dalam rentang valid`() {
        assertTrue(PortRangeParser.defaultPorts.all { it in 1..65535 })
    }

    @Test
    fun `kecepatan scan membatasi jumlah port`() {
        assertEquals(70, PortRangeParser.defaultPorts.take(ScanSpeed.SANGAT_STABIL.portCount).size)
        assertEquals(16, PortRangeParser.defaultPorts.take(ScanSpeed.CEPAT.portCount).size)
    }

    @Test
    fun `risiko port terkenal sesuai levelnya`() {
        assertEquals(PortRisk.KRITIS, PortRisks.riskOf(5555))
        assertEquals(PortRisk.KRITIS, PortRisks.riskOf(34567))
        assertEquals(PortRisk.KRITIS, PortRisks.riskOf(2375))
        assertEquals(PortRisk.TINGGI, PortRisks.riskOf(23))
        assertEquals(PortRisk.TINGGI, PortRisks.riskOf(8291))
        assertEquals(PortRisk.TINGGI, PortRisks.riskOf(3306))
        assertEquals(PortRisk.SEDANG, PortRisks.riskOf(22))
        assertEquals(PortRisk.RENDAH, PortRisks.riskOf(443))
        assertEquals(PortRisk.RENDAH, PortRisks.riskOf(9999))
    }
}
