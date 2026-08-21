package com.tasirin.network.radar.model

/** Parser input port kustom: contoh `22, 80, 8000-8010`. */
object CustomPortParser {

    fun parse(input: String): List<Int>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val ports = mutableSetOf<Int>()
        for (part in trimmed.split(',', ';')) {
            val value = part.trim()
            if (value.isEmpty()) continue
            val range = value.split('-', '..').map { it.trim() }
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
        }
        return ports.sorted()
    }

    fun resolve(input: String, fallback: List<Int>): List<Int> =
        parse(input) ?: fallback
}
