package com.tasirin.network.radar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tasirin.network.radar.scanner.ScanStopSignal

/**
 * Foreground service penjaga proses saat scan berjalan.
 * Menjaga prioritas proses agar scan tidak dibunuh sistem saat
 * layar terkunci / app di background. Progress diperbarui oleh ScanViewModel.
 */
class ScanService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ScanStopSignal.request()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Memulai scan...", 0))
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scan Berjalan", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("NetRadar — scan berjalan")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Berhenti",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, ScanService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val CHANNEL_ID = "scan_progress"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_DONE_ID = 2002
        const val ACTION_STOP = "com.tasirin.network.radar.action.STOP_SCAN"
    }
}
