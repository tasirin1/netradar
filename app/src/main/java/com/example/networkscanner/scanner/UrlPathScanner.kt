package com.example.networkscanner.scanner

import com.example.networkscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URL

class UrlPathScanner {

    private val paths = listOf(
        "/admin", "/admin/", "/login", "/login/", "/admin/login",
        "/config", "/config/", "/configuration", "/setup", "/setup/",
        "/backup", "/backup/", "/db", "/database", "/dump",
        "/wp-admin", "/administrator", "/manager",
        "/phpmyadmin", "/phpinfo.php", "/info.php",
        "/.env", "/.git/config", "/.git/HEAD", "/robots.txt",
        "/sitemap.xml", "/crossdomain.xml", "/.htaccess",
        "/api", "/api/", "/api/v1", "/swagger", "/swagger.json",
        "/graphql", "/graph", "/docs", "/status", "/health",
        "/shell", "/cmd", "/exec", "/console",
        "/upload", "/uploads", "/download", "/downloads",
        "/images", "/css", "/js", "/assets", "/static",
        "/server-status", "/server-info",
        "/actuator", "/actuator/health", "/actuator/info",
        "/cgi-bin/", "/cgi-bin/test.cgi",
        "/debug", "/test", "/test/", "/dev", "/dev/",
        "/panel", "/cpanel", "/webmail", "/mail",
        "/remote", "/remote/", "/desktop", "/vnc",
        "/axis-cgi/", "/view/view.shtml", "/index.html",
        "/snap.jpeg", "/snapshot.jpg", "/image.jpg",
        "/live", "/live/", "/stream", "/streaming",
        "/record", "/record/", "/playback", "/playback/"
    )

    fun scan(target: String): Flow<ScanEvent> = flow {
        var completed = 0
        val total = paths.size

        // Extract base URL from target
        val baseUrl = normalizeTarget(target)
        if (baseUrl == null) {
            emit(ScanEvent.Error("Invalid target: $target"))
            return@flow
        }

        for (path in paths) {
emit(ScanEvent.Progress(baseUrl, completed, total))
            val url = baseUrl.trimEnd('/') + path
            val result = checkPath(url)
            if (result != null) {
                emit(ScanEvent.UrlFound(result))
            }
            completed++
            delay(30)
        }

        emit(ScanEvent.Complete(ScanResult(
            type = ScanType.URL_PATH, target = target
        )))
    }

    private suspend fun checkPath(url: String): UrlDiscovery? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) NetRadar/2.0")
            conn.connect()

            val code = conn.responseCode
            val title = if (code == 200) {
                try {
                    val body = conn.inputStream.bufferedReader().use { it.readText().take(200) }
                    Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.take(60)
                } catch (_: Exception) { null }
            } else null

            conn.disconnect()

            if (code in listOf(200, 301, 302, 401, 403, 500)) {
                UrlDiscovery(url = url, statusCode = code, title = title)
            } else null
        } catch (_: Exception) { null }
    }

    private fun normalizeTarget(target: String): String? {
        var t = target.trim()
        if (t.isBlank()) return null
        if (!t.startsWith("http://") && !t.startsWith("https://")) {
            t = "http://$t"
        }
        return t
    }
}
