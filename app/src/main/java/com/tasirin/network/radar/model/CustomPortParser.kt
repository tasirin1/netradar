package com.tasirin.network.radar.model

/** Parser input port kustom: contoh `22, 80, 8000-8010`. */
object CustomPortParser {

    /** Batas port kustom sekali scan biasa: input selebar 1-65535 (65rb port,
     * 65rb coroutine sekaligus) ditolak agar tidak OOM — pakai deep scan
     * untuk cakupan penuh 1..65535 yang sudah di-chunk + dibatasi. */
    const val MAX_CUSTOM_PORTS = 1000

    fun parse(input: String): List<Int>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val ports = mutableSetOf<Int>()
        for (part in trimmed.split(',', ';')) {
            val value = part.trim()
            if (value.isEmpty()) continue
            val range = value.split("-", "..").map { it.trim() }
            if (range.size == 2) {
                val start = range[0].toIntOrNull() ?: return null
                val end = range[1].toIntOrNull() ?: return null
                if (start !in 1..65535 || end !in 1..65535 || start > end || end - start + 1 > 65_535) {
                    return null
                }
                ports.addAll(start..end)
            } else {
                val port = value.toIntOrNull() ?: return null
                if (port !in 1..65535) return null
                ports.add(port)
            }
            // Cek dini tiap bagian agar input raksasa berhenti sebelum alokasi penuh
            if (ports.size > MAX_CUSTOM_PORTS) return null
        }
        if (ports.isEmpty()) return null
        return ports.sorted()
    }

    fun resolveArray(input: String, fallback: IntArray): IntArray =
        parse(input)?.toIntArray() ?: fallback
}
