package com.tasirin.network.radar.scanner

import com.tasirin.network.radar.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URL

class UrlPathScanner {

    // Comprehensive path list. Destructive/logout paths are excluded at the bottom.
    private val rawPaths = listOf(
        // ── Admin / Panel ──
        "/admin", "/admin/", "/admin/login", "/admin/login/",
        "/login", "/login/", "/login/admin",
        "/administrator", "/manager", "/manager/",
        "/panel", "/panel/", "/cpanel", "/webadmin",
        "/dashboard", "/dashboard/", "/controlpanel",
        "/sysadmin", "/sysadmin/", "/management",
        "/admin_area", "/admin_area/",
        "/adminpanel", "/adminpanel/",

        // ── Config / Backup ──
        "/config", "/config/", "/configuration",
        "/backup", "/backup/", "/backups",
        "/dump", "/dump/",
        "/db", "/database", "/databases",
        "/sql", "/sql/", "/mysql",
        "/phpmyadmin", "/phpmyadmin/",
        "/pma", "/adminer",
        "/.env", "/.env.example",
        "/.git/config", "/.git/HEAD",
        "/.gitignore", "/.htaccess",
        "/robots.txt", "/sitemap.xml",
        "/crossdomain.xml", "/clientaccesspolicy.xml",
        "/wp-config.php", "/wp-config",
        "/config.php", "/config.php.bak",
        "/config.bak", "/config.old",
        "/configuration.php", "/settings",
        "/settings/", "/setup", "/setup/",
        "/install", "/install/", "/wizard",

        // ── API / Dev ──
        "/api", "/api/", "/api/v1", "/api/v1/",
        "/api/v2", "/api/v3", "/api/swagger",
        "/swagger", "/swagger/", "/swagger.json",
        "/swagger-ui", "/swagger-ui/",
        "/api-docs", "/openapi.json",
        "/graphql", "/graphql/", "/graph",
        "/docs", "/docs/", "/doc",
        "/health", "/health/", "/healthz",
        "/readyz", "/livez", "/status",
        "/status/", "/ping", "/ping/",
        "/version", "/version/", "/info",
        "/actuator", "/actuator/",
        "/actuator/health", "/actuator/info",
        "/actuator/env", "/actuator/metrics",
        "/actuator/beans", "/actuator/mappings",
        "/debug", "/debug/",
        "/test", "/test/", "/dev", "/dev/",
        "/phpinfo.php", "/info.php", "/test.php",
        "/server-status", "/server-info",

        // ── Shell / Exec ──
        "/shell", "/shell/",
        "/cmd", "/cmd/", "/cmd.php",
        "/exec", "/exec/",
        "/console", "/console/",
        "/terminal", "/terminal/",
        "/ssh", "/ssh/", "/bash",

        // ── Upload / File ──
        "/upload", "/upload/", "/uploads", "/uploads/",
        "/download", "/download/", "/downloads", "/downloads/",
        "/files", "/files/", "/file",
        "/backup_files", "/backup_files/",
        "/private", "/private/",
        "/tmp", "/tmp/", "/temp",
        "/logs", "/logs/", "/log",

        // ── Web Shell / Tools ──
        "/cgi-bin/", "/cgi-bin/test.cgi",
        "/cgi-bin/status", "/cgi-bin/admin",
        "/goform/", "/goform/login", "/goform/set",

        // ── Camera / CCTV ──
        "/axis-cgi/", "/axis-cgi/admin/",
        "/view/view.shtml", "/view/index.shtml",
        "/view/view.shtml?image=1",
        "/snap.jpeg", "/snapshot.jpg",
        "/image.jpg", "/image.jpeg",
        "/current.jpg", "/live.jpg",
        "/mjpg/video.mjpg", "/video.mjpg",
        "/live", "/live/", "/livecam",
        "/stream", "/stream/", "/streaming",
        "/record", "/record/", "/playback", "/playback/",

        // ── Router / Network ──
        "/status", "/status/", "/status.html",
        "/wifi", "/wifi/", "/wireless",
        "/wlan", "/wlan/", "/network",
        "/dhcp", "/dhcp/", "/lan",
        "/wan", "/wan/", "/internet",
        "/firewall", "/firewall/",
        "/nat", "/nat/", "/portforward",
        "/upnp", "/upnp/",
        "/ddns", "/ddns/",

        // ── Common Web ──
        "/index.html", "/index.php",
        "/default.html", "/default.php",
        "/home", "/home/", "/main",
        "/about", "/contact", "/help",
        "/search", "/search/",
        "/register", "/register/",
        "/remote", "/remote/", "/remote/login",
        "/desktop", "/desktop/", "/vnc",
        "/rdp", "/rdp/",
        "/webmail", "/mail", "/email",
        "/owa", "/exchange",
        "/remote/rdp", "/remote/desktop",
        "/webcon", "/webcon/",

        // ── IoT / Smart Home ──
        "/device", "/device/", "/devices",
        "/sensor", "/sensor/", "/sensors",
        "/smart", "/smart/",
        "/power", "/power/", "/energy",

        // ── Storage / Shares ──
        "/storage", "/storage/",
        "/disk", "/disk/", "/disks",
        "/volume", "/volume/", "/volumes",
        "/share", "/share/", "/shares",
        "/ftp", "/ftp/", "/samba",
        "/nfs", "/nfs/",
        "/media", "/media/", "/movies",
        "/music", "/photos", "/pictures",
        "/video", "/videos",

        // ── WebShells / Backdoors ──
        "/shell.php", "/cmd.php", "/exec.php",
        "/admin.php", "/backdoor.php",
        "/webshell.php", "/r57.php", "/c99.php",
        "/b374k.php", "/wso.php",
        "/.shell", "/.backdoor"
    )

    // Paths CONTAINING these keywords are DESTRUCTIVE and will be filtered out
    private val destructivePatterns = listOf(
        "logout", "signout", "sign-out", "log-out", "logoff", "log-off",
        "exit", "quit", "end-session", "kill",
        "delete", "remove", "destroy", "wipe", "erase",
        "SysToolReboot", "reboot", "restart", "shutdown",
        "factory", "defaults", "reset",
        "SysToolUpgrade", "firmware", "flash",
        "clear", "purge", "uninstall"
    )

    // Only keep non-destructive paths
    private val paths by lazy {
        rawPaths.filter { path ->
            val lower = path.lowercase()
            !destructivePatterns.any { pattern -> lower.contains(pattern) }
        }
    }

    fun scan(target: String): Flow<ScanEvent> = channelFlow {
        val baseUrl = normalizeTarget(target)
        if (baseUrl == null) {
            send(ScanEvent.Error("Invalid target: $target"))
            return@channelFlow
        }

        if (paths.isEmpty()) {
            send(ScanEvent.Error("No valid paths to scan"))
            return@channelFlow
        }

        val total = paths.size
        var completed = 0

        send(ScanEvent.Progress(baseUrl, 0, total))

        // Parallel scanning: process paths in batches of 30
        withContext(Dispatchers.IO) {
            paths.chunked(30).forEach { batch ->
                ScanPause.checkPause()
                val deferred = batch.map { path ->
                    async {
                        val url = baseUrl.trimEnd('/') + path
                        url to checkPath(url)
                    }
                }
                deferred.forEach { deferred ->
                    val (url, result) = deferred.await()
                    completed++
                    send(ScanEvent.Progress(url, completed, total))
                    if (result != null) {
                        send(ScanEvent.UrlFound(result))
                    }
                }
            }
        }

        send(ScanEvent.Complete(ScanResult(
            type = ScanType.URL_PATH, target = target
        )))
    }

    private suspend fun checkPath(url: String): UrlDiscovery? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 800
            conn.readTimeout = 500
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) NetRadar/2.0")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            conn.connect()

            val code = conn.responseCode
            var title: String? = null

            // Read title for interesting responses
            if (code == 200) {
                try {
                    val body = conn.inputStream.bufferedReader().use {
                        val buf = CharArray(500); val n = it.read(buf, 0, 500)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                    title = Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
                        .find(body)?.groupValues?.get(1)?.take(80)?.trim()
                } catch (_: Exception) { }
            } else if (code in listOf(401, 403)) {
                try {
                    val body = conn.errorStream?.bufferedReader()?.use {
                        val buf = CharArray(300); val n = it.read(buf, 0, 300)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                    title = body?.let {
                        Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
                            .find(it)?.groupValues?.get(1)?.take(60)?.trim()
                    }
                } catch (_: Exception) { }
            }

            conn.disconnect()

            // Report interesting status codes (exclude not-found)
            if (code in listOf(200, 301, 302, 303, 307, 308, 401, 403, 500, 502, 503)) {
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
        // Strip to base host:port only
        return try {
            val u = URL(t)
            val port = if (u.port > 0 && u.port != 80 && u.port != 443) ":${u.port}" else ""
            "${u.protocol}://${u.host}$port"
        } catch (_: Exception) {
            t.trimEnd('/')
        }
    }
}
