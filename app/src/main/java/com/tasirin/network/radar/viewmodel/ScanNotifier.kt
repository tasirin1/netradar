package com.tasirin.network.radar.viewmodel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tasirin.network.radar.ScanService
import com.tasirin.network.radar.model.HostInfo

/** Pusat notifikasi dan foreground service agar ViewModel fokus ke state scan. */
class ScanNotifier(private val context: Context) {

    fun startScanService() {
        try {
            val intent = Intent(context, ScanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Foreground service gagal dimulai", e)
        }
    }

    fun stopScanService() {
        try {
            context.stopService(Intent(context, ScanService::class.java))
            NotificationManagerCompat.from(context).cancel(ScanService.NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Foreground service gagal dihentikan", e)
        }
    }

    fun notifyNewDevice(host: HostInfo) {
        val detail = buildString {
            host.hostname?.takeIf { it != host.ip }?.let { append(it) }
            host.macVendor?.let { if (isNotEmpty()) append(" · "); append(it) }
        }
        post(
            channel = CHANNEL_NEW_DEVICE,
            channelName = "Perangkat Baru",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            title = "Perangkat baru: ${host.ip}",
            text = detail.ifBlank { "Host baru terdeteksi di jaringan" },
            id = host.ip.hashCode(),
            ongoing = false
        )
    }

    fun notifyImportantOffline(ip: String) = post(
        channel = CHANNEL_IMPORTANT,
        channelName = "Perangkat Penting",
        importance = NotificationManager.IMPORTANCE_HIGH,
        title = "⚠ Perangkat penting offline: $ip",
        text = "Perangkat favorit tidak merespons ping.",
        id = ip.hashCode() + 9999,
        ongoing = false
    )

    fun notifyFavoriteBackOnline(ip: String, name: String) = post(
        channel = CHANNEL_IMPORTANT,
        channelName = "Perangkat Penting",
        importance = NotificationManager.IMPORTANCE_HIGH,
        title = "📱 $name kembali online",
        text = "$ip merespons ping lagi.",
        id = ip.hashCode() + 7777,
        ongoing = false
    )

    fun notifyProgress(title: String, text: String, percent: Int) = post(
        channel = ScanService.CHANNEL_ID,
        channelName = "Scan Berjalan",
        importance = NotificationManager.IMPORTANCE_LOW,
        title = title,
        text = text,
        id = ScanService.NOTIFICATION_ID,
        ongoing = true,
        progress = percent
    )

    fun notifyDone(title: String, text: String) = post(
        channel = ScanService.CHANNEL_ID,
        channelName = "Scan Berjalan",
        importance = NotificationManager.IMPORTANCE_LOW,
        title = title,
        text = text,
        id = ScanService.NOTIFICATION_DONE_ID,
        ongoing = false
    )

    @SuppressLint("MissingPermission")
    private fun post(
        channel: String,
        channelName: String,
        importance: Int,
        title: String,
        text: String,
        id: Int,
        ongoing: Boolean,
        progress: Int? = null
    ) {
        try {
            if (!hasPermission()) return
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(channel, channelName, importance))
            }
            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(channel == ScanService.CHANNEL_ID)
            progress?.let { builder.setProgress(100, it, it == 0) }
            if (channel == ScanService.CHANNEL_ID) {
                val stopIntent = Intent(context, ScanService::class.java)
                    .setAction(ScanService.ACTION_STOP)
                val stopPending = PendingIntent.getService(
                    context,
                    0,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Berhenti", stopPending)
            }
            manager.notify(id, builder.build())
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Notifikasi gagal dikirim", e)
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "NetRadarNotifier"
        const val CHANNEL_NEW_DEVICE = "new_device"
        const val CHANNEL_IMPORTANT = "important_device"
    }
}
