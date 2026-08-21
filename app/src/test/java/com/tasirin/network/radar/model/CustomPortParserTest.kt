package com.tasirin.network.radar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomPortParserTest {

    @Test
    fun `input kosong memakai fallback`() {
        assertNull(CustomPortParser.parse(""))
        assertEquals(listOf(80, 443), CustomPortParser.resolve(" ", listOf(80, 443)))
    }

    @Test
    fun `koma dan rentang diparse urut tanpa duplikat`() {
        assertEquals(
            listOf(22, 80, 8000, 8001, 8002),
            CustomPortParser.parse("80, 22, 8000-8002")
        )
    }

    @Test
    fun `input tidak valid ditolak`() {
        assertNull(CustomPortParser.parse("abc"))
        assertNull(CustomPortParser.parse("0"))
        assertNull(CustomPortParser.parse("65536"))
        assertNull(CustomPortParser.parse("100-50"))
    }
}
