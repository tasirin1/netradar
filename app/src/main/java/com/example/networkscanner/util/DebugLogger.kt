package com.example.networkscanner.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple in-app debug logger & crash reporter.
 * Logs are saved to a file and can be shared.
 */
object DebugLogger {

    private const val MAX_LOG_SIZE = 500
    private const val FILE_NAME = "netradar_debug.log"

    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx
        // Install crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("CRASH", "${throwable.javaClass.simpleName}: ${throwable.message}")
            for (ste in throwable.stackTrace.take(20)) {
                log("CRASH", "  at ${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
            }
            throwable.cause?.let { cause ->
                log("CRASH", "Caused by: ${cause.javaClass.simpleName}: ${cause.message}")
                for (ste in cause.stackTrace.take(10)) {
                    log("CRASH", "  at ${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
                }
            }
            saveToFile()
            defaultHandler?.uncaughtException(thread, throwable)
        }
        log("INIT", "DebugLogger initialized")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val entry = "$time [$tag] $message"
        logs.add(entry)
        if (logs.size > MAX_LOG_SIZE) {
            logs.removeAt(0)
        }
        android.util.Log.d("NetRadar/$tag", message)
    }

    @Synchronized
    fun getLogText(): String {
        return logs.joinToString("\n")
    }

    @Synchronized
    fun getLogFile(): File? {
        val ctx = context ?: return null
        val file = File(ctx.cacheDir, FILE_NAME)
        saveToFileInternal(file)
        return file
    }

    @Synchronized
    fun saveToFile() {
        val ctx = context ?: return
        saveToFileInternal(File(ctx.filesDir, FILE_NAME))
    }

    private fun saveToFileInternal(file: File) {
        try {
            FileWriter(file).use { writer ->
                writer.write("=== NetRadar Debug Log ===\n")
                writer.write("Date: ${fileDateFormat.format(Date())}\n")
                writer.write("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                writer.write("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                writer.write("================================\n\n")
                logs.forEach { writer.write("$it\n") }
            }
        } catch (_: Exception) {}
    }

    fun shareLog(ctx: Context) {
        val file = getLogFile() ?: return
        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NetRadar Debug Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share Debug Log"))
        } catch (_: Exception) {}
    }
}
