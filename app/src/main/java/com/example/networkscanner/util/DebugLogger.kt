package com.example.networkscanner.util


import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * In-app debug logger with automatic crash log to Downloads folder.
 * After a crash, user can find the log in /Downloads/NetRadar_crash.log
 * without needing to open the app.
 */
object DebugLogger {

    private const val MAX_LOG_SIZE = 500
    private const val CRASH_FILENAME = "NetRadar_crash.log"

    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Capture crash to internal log
            log("CRASH", "${throwable.javaClass.simpleName}: ${throwable.message}")
            for (ste in throwable.stackTrace.take(30)) {
                log("CRASH", "  at ${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
            }
            throwable.cause?.let { cause ->
                log("CAUSE", "${cause.javaClass.simpleName}: ${cause.message}")
                for (ste in cause.stackTrace.take(15)) {
                    log("CAUSE", "  at ${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
                }
            }
            // Save to internal (for in-app share)
            saveToInternal()
            // Save to Downloads (accessible without opening app)
            saveToDownloads(ctx)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        log("INIT", "DebugLogger ready")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val entry = "$time [$tag] $message"
        logs.add(entry)
        if (logs.size > MAX_LOG_SIZE) logs.removeAt(0)
        android.util.Log.d("NetRadar/$tag", message)
    }

    @Synchronized
    fun getLogText(): String = logs.joinToString("\n")

    private fun saveToInternal() {
        val ctx = context ?: return
        try {
            FileWriter(File(ctx.filesDir, CRASH_FILENAME)).use { it.write(buildLog()) }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun saveToDownloads(ctx: Context) {
        try {
            val logText = buildLog()

            if (Build.VERSION.SDK_INT >= 29) {
                // Android 10+ — use MediaStore (no permission needed)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, CRASH_FILENAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    ctx.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(logText.toByteArray())
                    }
                }
            } else {
                // Android 9 and below
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dir.exists() || dir.mkdirs()) {
                    FileWriter(File(dir, CRASH_FILENAME)).use { it.write(logText) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildLog(): String {
        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════════════════╗")
        sb.appendLine("║       NetRadar Crash Report          ║")
        sb.appendLine("╚══════════════════════════════════════╝")
        sb.appendLine()
        sb.appendLine("Time:   ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("App:    ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine()
        sb.appendLine("─────────────────────────────────────")
        logs.forEach { sb.appendLine(it) }
        return sb.toString()
    }

    fun shareLog(ctx: Context) {
        val file = File(ctx.filesDir, CRASH_FILENAME)
        if (!file.exists()) {
            saveToInternal()
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NetRadar Debug Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share Debug Log"))
        } catch (_: Exception) {}
    }

    private fun getVersion(ctx: Context): String {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (_: Exception) { "unknown" }
    }
}

