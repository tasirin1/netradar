package com.tasirin.network.radar.scanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Jembatan tombol berhenti di notifikasi ke ScanViewModel. */
object ScanStopSignal {
    private val _requests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = _requests.asStateFlow()

    fun request() {
        _requests.value += 1
    }
}
