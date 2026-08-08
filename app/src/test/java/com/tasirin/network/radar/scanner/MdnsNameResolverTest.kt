package com.tasirin.network.radar.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Verifikasi parser jawaban mDNS (PTR/SRV/A) dan header SSDP. */
class MdnsNameResolverTest {

    private fun nameToBytes(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        name.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        return out.toByteArray()
    }

    @Test
    fun `parse jawaban mDNS dengan PTR SRV dan A`() {
        // Header: ID 0, flags 0x8400, QD=0, AN=3, NS=0, AR=0
        val pkt = ByteArrayOutputStream()
        pkt.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 3, 0, 0, 0, 0))

        // PTR: _airplay._tcp.local -> "Ruang TV._airplay._tcp.local"
        pkt.write(nameToBytes("_airplay._tcp.local"))
        pkt.write(byteArrayOf(0, 12, 0, 1, 0, 0, 0, 120))
        val ptrRdata = nameToBytes("Ruang TV._airplay._tcp.local")
        pkt.write(byteArrayOf(0, ptrRdata.size.toByte()))
        pkt.write(ptrRdata)

        // SRV: instance -> target "Ruang-TV.local" port 7000
        pkt.write(nameToBytes("Ruang TV._airplay._tcp.local"))
        pkt.write(byteArrayOf(0, 33, 0, 1, 0, 0, 0, 120))
        val srvRdata = ByteArrayOutputStream()
        srvRdata.write(byteArrayOf(0, 0, 0, 0, 0x1B, 0x58)) // priority, weight, port 7000
        srvRdata.write(nameToBytes("Ruang-TV.local"))
        val srvBytes = srvRdata.toByteArray()
        pkt.write(byteArrayOf(0, srvBytes.size.toByte()))
        pkt.write(srvBytes)

        // A: Ruang-TV.local -> 192.168.1.50
        pkt.write(nameToBytes("Ruang-TV.local"))
        pkt.write(byteArrayOf(0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 192.toByte(), 168.toByte(), 1, 50))

        val result = MdnsNameResolver.parseDns(pkt.toByteArray(), pkt.size())
        assertEquals(mapOf("192.168.1.50" to "Ruang TV"), result)
    }

    @Test
    fun `parse jawaban mDNS tanpa instance PTR tidak menghasilkan nama`() {
        val pkt = ByteArrayOutputStream()
        pkt.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 0))
        // A saja, tanpa PTR/SRV
        pkt.write(nameToBytes("server.local"))
        pkt.write(byteArrayOf(0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 10.toByte(), 0, 0, 5))
        assertTrue(MdnsNameResolver.parseDns(pkt.toByteArray(), pkt.size()).isEmpty())
    }

    @Test
    fun `paket pendek dikembalikan kosong`() {
        assertTrue(MdnsNameResolver.parseDns(byteArrayOf(1, 2, 3), 3).isEmpty())
    }

    @Test
    fun `parse balasan SSDP dari tv`() {
        val text = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: http://192.168.1.50:7676/device.xml\r\n" +
            "SERVER: LG Electronics UPnP/1.0 TV\r\n" +
            "USN: uuid:abc123::urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        assertEquals("LG Electronics Media Player",
            MdnsNameResolver.parseSsdp(text.toByteArray(), text.length))
    }

    @Test
    fun `parse balasan SSDP yang bukan 200 null`() {
        val text = "HTTP/1.1 404 Not Found\r\n\r\n"
        assertNull(MdnsNameResolver.parseSsdp(text.toByteArray(), text.length))
    }
}
