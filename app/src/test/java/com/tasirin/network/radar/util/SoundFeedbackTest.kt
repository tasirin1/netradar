package com.tasirin.network.radar.util

import android.media.ToneGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFeedbackTest {

    @Test
    fun `nada sesuai jenis port`() {
        assertEquals(ToneGenerator.TONE_PROP_BEEP2, SoundFeedback.toneForPort(22))
        assertEquals(ToneGenerator.TONE_PROP_ACK, SoundFeedback.toneForPort(445))
        assertEquals(ToneGenerator.TONE_CDMA_PIP, SoundFeedback.toneForPort(554))
        assertEquals(ToneGenerator.TONE_PROP_BEEP, SoundFeedback.toneForPort(80))
    }
}
