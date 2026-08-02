package com.tasirin.network.radar.util

import android.content.Context
import com.tasirin.network.radar.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simpan jejak crash terakhir agar bisa dibaca & dilaporkan. */
object CrashLog {

    private const val FILE = "netradar_crash.txt"

    fun save(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val header = buildString {
                appendLine("Waktu: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                appendLine("Versi: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                appendLine()
            }
            context.openFileOutput(FILE, Context.MODE_PRIVATE).use { out ->
                out.write((header + sw.toString()).toByteArray())
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
