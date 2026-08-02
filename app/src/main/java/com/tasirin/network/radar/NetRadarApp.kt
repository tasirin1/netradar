package com.tasirin.network.radar

import android.app.Application
import com.tasirin.network.radar.util.AppForeground
import com.tasirin.network.radar.util.CrashLog

/** Aplikasi: menangkap uncaught exception agar jejak crash tersimpan untuk diagnosa. */
class NetRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppForeground.register()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { CrashLog.save(this, throwable) } catch (_: Exception) { }
            previous?.uncaughtException(thread, throwable) ?: Runtime.getRuntime().halt(2)
        }
    }
}
