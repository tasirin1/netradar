package com.tasirin.network.radar.scanner

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanControlTest {

    @Test
    fun `checkpoint resume hanya dipakai sekali`() {
        ScanCheckpoint.setResume(2, 17)
        assertEquals(2 to 17, ScanCheckpoint.takeIfResume())
        assertNull(ScanCheckpoint.takeIfResume())
    }

    @Test
    fun `pause global bisa dilanjutkan`() = runTest {
        ScanPause.resume()
        assertFalse(ScanPause.paused)
        ScanPause.pause()
        assertTrue(ScanPause.paused)
        val job = launch { ScanPause.checkPause() }
        delay(50)
        assertTrue(job.isActive)
        ScanPause.resume()
        job.join()
        assertTrue(job.isCompleted)
    }
}
