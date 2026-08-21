package com.tasirin.network.radar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanTest {

    @Test
    fun `magic packet berisi enam FF dan MAC enam belas kali`() {
        val packet = WakeOnLan.buildMagicPacket("AA:BB:CC:DD:EE:FF")
        assertNotNull(packet)
        packet!!.let {
            assertEquals(102, it.size)
            assertTrue(it.take(6).all { byte -> byte == 0xFF.toByte() })
            for (index in 0 until 16) {
                val start = 6 + index * 6
                assertEquals(0xAA.toByte(), it[start])
                assertEquals(0xFF.toByte(), it[start + 5])
            }
        }
    }

    @Test
    fun `mac tidak valid menghasilkan null`() {
        assertNull(WakeOnLan.buildMagicPacket("AA:BB:CC"))
        assertNull(WakeOnLan.buildMagicPacket("ZZ:BB:CC:DD:EE:FF"))
    }
}
