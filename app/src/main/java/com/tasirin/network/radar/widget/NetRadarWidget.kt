package com.tasirin.network.radar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.tasirin.network.radar.MainActivity
import com.tasirin.network.radar.R
import com.tasirin.network.radar.util.FavoritesStore
import com.tasirin.network.radar.util.ResultsStore
import com.tasirin.network.radar.util.UptimeStore

/** Widget home screen: daftar perangkat penting + status; tap untuk membuka aplikasi. */
class NetRadarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        private val ROW_IDS = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3, R.id.widget_row4)

        private fun buildViews(context: Context): RemoteViews {
            val (hosts, _) = ResultsStore.load(context)
            val favorites = FavoritesStore.load(context)
            val uptime = UptimeStore.load(context)
            val favHosts = hosts.filter { it.ip in favorites }
            val online = favHosts.count { h -> uptime[h.ip]?.lastOrNull()?.online != false }

            val views = RemoteViews(context.packageName, R.layout.widget_netradar).apply {
                val text = if (favorites.isEmpty())
                    "${hosts.size} host terdeteksi — tandai perangkat penting untuk tampil di sini"
                else "$online/${favHosts.size} penting online · ${hosts.size} host"
                setTextViewText(R.id.widget_status, text)

                ROW_IDS.forEachIndexed { i, rowId ->
                    val host = favHosts.getOrNull(i)
                    if (host != null) {
                        val last = uptime[host.ip]?.lastOrNull()
                        val dot = when (last?.online) {
                            true -> "●"
                            false -> "○"
                            null -> "?"
                        }
                        val color = when (last?.online) {
                            true -> 0xFF2E7D32.toInt()
                            false -> 0xFFC62828.toInt()
                            null -> 0xFFE65100.toInt()
                        }
                        val name = host.label ?: host.hostname?.takeIf { it != host.ip } ?: host.ip
                        setTextViewText(rowId, "$dot $name · ${host.ip}")
                        setTextColor(rowId, color)
                        setViewVisibility(rowId, android.view.View.VISIBLE)
                    } else {
                        setViewVisibility(rowId, android.view.View.GONE)
                    }
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val openApp = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), flags)
                setOnClickPendingIntent(R.id.widget_root, openApp)
                ROW_IDS.forEach { rowId -> setOnClickPendingIntent(rowId, openApp) }
            }
            return views
        }

        /** Perbarui semua instance widget dengan teks terbaru. */
        fun pushUpdate(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, NetRadarWidget::class.java))
                val views = buildViews(context)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (_: Exception) { }
        }
    }
}
