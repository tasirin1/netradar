package com.tasirin.network.radar.scanner

/**
 * Posisi resume scan, dibaca sekali oleh ScanLoop saat scan dimulai.
 * Diaktifkan ViewModel hanya jika target & jenis scan sesuai checkpoint tersimpan.
 */
object ScanCheckpoint {
    @Volatile
    private var resumeSubnetIndex = -1
    @Volatile
    private var resumeHostOffset = 0

    /** Aktifkan resume dari posisi tertentu; konsumsi sekali oleh ScanLoop. */
    fun setResume(subnetIndex: Int, hostOffset: Int) {
        resumeSubnetIndex = subnetIndex
        resumeHostOffset = hostOffset
    }

    fun reset() {
        resumeSubnetIndex = -1
        resumeHostOffset = 0
    }

    /** Ambil posisi resume (jika ada) lalu nonaktifkan supaya scan berikutnya mulai baru. */
    fun takeIfResume(): Pair<Int, Int>? {
        return if (resumeSubnetIndex >= 0) {
            val r = resumeSubnetIndex to resumeHostOffset
            reset()
            r
        } else null
    }
}
