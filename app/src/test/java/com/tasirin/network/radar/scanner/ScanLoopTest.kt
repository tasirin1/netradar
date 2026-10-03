package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.ScanEvent
import com.tasirin.network.radar.model.ScanSpeed
import com.tasirin.network.radar.util.NetworkUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard regresi ScanLoop: progres retry, batas retry, dan clamp checkpoint basi. */
class ScanLoopTest {

    private fun subnet(prefix: String) = NetworkUtils.SubnetTarget(prefix)

    @Test
    fun `progres retry tidak melebihi total`() = runTest {
        ScanPause.resume()
        val events = mutableListOf<ScanEvent>()
        // Semua host hilang → retry jalan, tapi completed tidak dihitung ganda
        ScanLoop.scanSubnets(listOf(subnet("192.168.99")), ScanSpeed.SEDANG, "Uji",
            scanOne = { null },
            onEvent = { events.add(it) })
        val progresses = events.filterIsInstance<ScanEvent.Progress>()
        assertTrue(progresses.isNotEmpty())
        progresses.forEach {
            assertTrue("progres ${it.current}/${it.total} melebihi total", it.current <= it.total)
        }
        assertTrue(events.any { it is ScanEvent.Progress && it.ip.startsWith("Retry") })
    }

    @Test
    fun `retry dilewati bila host hilang melebihi batas`() = runTest {
        ScanPause.resume()
        val events = mutableListOf<ScanEvent>()
        // 10 subnet × 254 = 2540 hilang > MAX_RETRY_HOSTS → tanpa fase retry
        val subnets = (0 until 10).map { subnet("10.9.$it") }
        ScanLoop.scanSubnets(subnets, ScanSpeed.SEDANG, "Uji",
            scanOne = { null },
            onEvent = { events.add(it) })
        assertTrue(events.none { it is ScanEvent.Progress && it.ip.startsWith("Retry") })
    }

    @Test
    fun `checkpoint basi tidak crash`() = runTest {
        ScanPause.resume()
        // Offset 999 pada subnet 254 host: wajib dijepit, bukan IndexOutOfBounds
        ScanCheckpoint.setResume(0, 999)
        val events = mutableListOf<ScanEvent>()
        ScanLoop.scanSubnets(listOf(subnet("192.168.98")), ScanSpeed.SEDANG, "Uji",
            scanOne = { null },
            onEvent = { events.add(it) })
        assertTrue(events.isNotEmpty())
        ScanCheckpoint.reset()
    }
}
