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

/** Widget home screen: ringkasan hasil scan; tap untuk membuka aplikasi. */
class NetRadarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val hosts = ResultsStore.load(context).first.size
        val favorites = FavoritesStore.load(context).size
        val text = buildText(hosts, favorites)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, text))
        }
    }

    companion object {
        private fun buildText(hosts: Int, favorites: Int) =
            "$hosts host terdeteksi · $favorites penting"

        private fun buildViews(context: Context, text: String): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_netradar).apply {
                setTextViewText(R.id.widget_status, text)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), flags))
            }

        /** Perbarui semua instance widget dengan teks terbaru. */
        fun pushUpdate(context: Context, hosts: Int, favorites: Int) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, NetRadarWidget::class.java))
                val views = buildViews(context, buildText(hosts, favorites))
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (_: Exception) { }
        }
    }
}
