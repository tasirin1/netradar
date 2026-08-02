package com.tasirin.network.radar.util

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/** Simpan jejak crash terakhir agar bisa dibaca & dilaporkan. */
object CrashLog {

    private const val FILE = "netradar_crash.txt"

    fun save(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { out ->
                out.write(sw.toString())
            }
        } catch (_: Exception) { }
    }

    fun read(context: Context): String? = try {
        context.openFileInput(FILE).bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun clear(context: Context) {
        try { context.deleteFile(FILE) } catch (_: Exception) { }
    }
}
