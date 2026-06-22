package com.example.networkscanner

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var tvResults: TextView
    private lateinit var tvSummary: TextView
    private lateinit var cardResults: View
    private var isScanning = false

    // Ports for quick scan (most common web ports only)
    private val quickPorts = intArrayOf(80, 443, 8080, 8443, 8000, 3000, 5000, 8888, 9000, 81, 444, 8291, 2000)
    // Ports for full scan (all services)
    private val fullPorts = intArrayOf(
        21, 22, 23, 53, 80, 81, 82, 88, 110, 111, 123, 135, 139, 143, 161, 389, 443, 444,
        445, 500, 502, 514, 546, 547, 554, 587, 623, 631, 636, 993, 995, 1080, 1194, 1433,
        1494, 1521, 1701, 1723, 1883, 1900, 2000, 2082, 2083, 2086, 2087, 2095, 2096, 2181,
        2222, 2375, 2376, 3000, 3128, 3260, 3299, 3306, 3389, 3390, 4000, 4222, 4343, 4443,
        4848, 5000, 5001, 5432, 5555, 5631, 5666, 5800, 5900, 5901, 5984, 6000, 6001, 6379,
        6646, 6666, 7000, 7001, 7070, 7077, 7443, 7547, 7675, 7777, 8000, 8008, 8009, 8080,
        8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8180, 8222, 8243, 8280,
        8291, 8443, 8444, 8530, 8531, 8649, 8800, 8834, 8880, 8888, 8889, 8983, 9000, 9001,
        9043, 9060, 9080, 9090, 9091, 9100, 9200, 9290, 9300, 9418, 9443, 9600, 9800, 9999,
        10000, 10001, 10080, 11211, 12345, 13337, 16010, 16379, 17000, 17001, 20000, 22000,
        25565, 27017, 31337, 32400, 32764, 49152, 49154, 49155, 49156, 50000, 50070, 50100,
        50200, 61616, 64738, 65535
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.dotStatus)
        tvResults = findViewById(R.id.tvResults)
        tvSummary = findViewById(R.id.tvSummary)
        cardResults = findViewById(R.id.cardResults)

        findViewById<MaterialButton>(R.id.btnQuickScan).setOnClickListener { quickScan() }
        findViewById<MaterialButton>(R.id.btnFullScan).setOnClickListener { fullScan() }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            cardResults.visibility = View.GONE
            status("Ready to scan", "#78909C", false)
        }
    }

    private fun quickScan() {
        if (isScanning) { toast("Already scanning!"); return }
        status("Quick scan: common web ports...", "#E65100", true)
        startScan(quickPorts)
    }

    private fun fullScan() {
        if (isScanning) { toast("Already scanning!"); return }
        status("Full scan: 200+ ports on all hosts...", "#E65100", true)
        AlertDialog.Builder(this)
            .setTitle("Full Scan")
            .setMessage("Scanning 200+ ports on all 254 IPs.\nThis takes 2-3 minutes.")
            .setPositiveButton("Start") { _, _ -> startScan(fullPorts) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startScan(ports: IntArray) {
        isScanning = true
        cardResults.visibility = View.VISIBLE
        tvResults.text = "Scanning...\n"
        tvSummary.text = ""

        bg.execute {
            val startTime = System.currentTimeMillis()
            val results = mutableListOf<String>()
            val found = AtomicInteger(0)
            val totalPorts = ports.size

            try {
                // Get local IP
                val localIp = InetAddress.getLocalHost()
                val localAddr = localIp.hostAddress ?: "192.168.0.101"
                val parts = localAddr.split(".")
                if (parts.size != 4) {
                    uiPost("Invalid local IP", "#C62828", false)
                    isScanning = false
                    return@execute
                }
                val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."

                results.add("── Scanning ${prefix}0/24 ──")
                results.add("Local IP: $localAddr\n")

                for (i in 1..254) {
                    if (!isScanning) {
                        results.add("\n── Cancelled ──")
                        break
                    }
                    val ip = "$prefix$i"
                    val ipServices = mutableListOf<String>()
                    val ipPorts = mutableListOf<Int>()

                    for (pi in ports.indices) {
                        val port = ports[pi]
                        try {
                            val s = Socket()
                            s.connect(InetSocketAddress(ip, port), 250)
                            if (s.isConnected) {
                                s.close()
                                ipPorts.add(port)
                                val service = guessService(port)
                                var detail = "  Port $port $service"

                                // HTTP detection
                                if (port in intArrayOf(80, 81, 443, 444, 3000, 4000, 5000, 5001,
                                        7000, 7070, 7443, 7547, 8000, 8080, 8081, 8082, 8083,
                                        8084, 8085, 8086, 8087, 8088, 8089, 8090, 8180, 8222,
                                        8243, 8280, 8443, 8444, 8530, 8531, 8649, 8800, 8834,
                                        8880, 8888, 8889, 8983, 9000, 9001, 9043, 9060, 9080,
                                        9090, 9091, 9100, 9200, 9290, 9300, 9418, 9443, 9600,
                                        9800, 9999, 10000, 10001, 10080, 17000, 17001, 20000,
                                        22000, 32400, 32764, 49154, 49155, 49156, 50000, 50100,
                                        50200, 61616, 64738, 65535)) {
                                    try {
                                        val protocol = if (port == 443 || port == 8443 || port == 7443)
                                            "https" else "http"
                                        val conn = URL("$protocol://$ip").openConnection() as java.net.HttpURLConnection
                                        conn.connectTimeout = 1000
                                        conn.readTimeout = 1000
                                        conn.instanceFollowRedirects = false
                                        val code = conn.responseCode
                                        val server = conn.getHeaderField("Server")
                                        val loc = conn.getHeaderField("Location")
                                        detail += " [HTTP $code"
                                        if (server != null) detail += " · $server"
                                        if (loc != null && loc.length < 80) detail += " → $loc"
                                        detail += "]"
                                        conn.disconnect()
                                    } catch (_: Exception) {
                                        detail += " [TCP open]"
                                    }
                                } else {
                                    detail += " [TCP open]"
                                }
                                ipServices.add(detail)
                            }
                        } catch (_: Exception) { }

                        // Update progress every ~50 ports
                        if (pi % 50 == 0 && pi > 0) {
                            val percent = (pi * 100 / totalPorts).coerceAtMost(100)
                            uiPost("Scanning... $percent%", "#E65100", true)
                        }
                    }

                    if (ipServices.isNotEmpty()) {
                        found.incrementAndGet()
                        results.add("\n$ip")
                        results.addAll(ipServices)
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                val summary = "\n── Done in ${elapsed / 1000}s ──\n${found.get()} host(s) with open ports"

                uiPost("Found ${found.get()} host(s)", "#2E7D32", true)
                uiPostResult(summary, results.joinToString("\n"))

            } catch (e: Exception) {
                uiPost("Error: ${e.message}", "#C62828", false)
                uiPostResult("", results.joinToString("\n") + "\n\nError: ${e.message}")
            }

            isScanning = false
        }
    }

    private fun guessService(port: Int): String = when (port) {
        21 -> "FTP"; 22 -> "SSH"; 23 -> "Telnet"; 53 -> "DNS"
        80, 81, 82, 88 -> "HTTP"; 110 -> "POP3"; 111 -> "RPC"
        123 -> "NTP"; 135 -> "MSRPC"; 139 -> "NetBIOS"; 143 -> "IMAP"
        161 -> "SNMP"; 389 -> "LDAP"; 443, 444 -> "HTTPS"; 445 -> "SMB"
        500 -> "IKE"; 502 -> "Modbus"; 514 -> "Syslog"; 546, 547 -> "DHCPv6"
        554 -> "RTSP"; 587 -> "SMTP"; 623 -> "IPMI"; 631 -> "IPP"
        636 -> "LDAPS"; 993 -> "IMAPS"; 995 -> "POP3S"; 1080 -> "SOCKS"
        1194 -> "OpenVPN"; 1433 -> "MSSQL"; 1494 -> "Citrix"
        1521 -> "Oracle"; 1701 -> "L2TP"; 1723 -> "PPTP"
        1883 -> "MQTT"; 1900 -> "UPnP"; 2000 -> "Bandwidth"
        2082, 2083 -> "cPanel"; 2086, 2087 -> "WHM"
        2095, 2096 -> "WebMail"; 2181 -> "ZooKeeper"; 2222 -> "SSH-Alt"
        2375, 2376 -> "Docker"; 3000 -> "Web-Dev"; 3128 -> "Squid"
        3260 -> "iSCSI"; 3299 -> "Web-Dev2"; 3306 -> "MySQL"
        3389, 3390 -> "RDP"; 4000 -> "Web-Alt"; 4222 -> "Web-Alt2"
        4343, 4443 -> "HTTPS-Alt"; 4848 -> "GlassFish"
        5000, 5001 -> "Web-Alt3"; 5432 -> "PostgreSQL"
        5555 -> "ADB"; 5631 -> "VNC"; 5666 -> "Nagios"
        5800, 5900, 5901 -> "VNC"; 5984 -> "CouchDB"; 6000, 6001 -> "X11"
        6379 -> "Redis"; 6646 -> "Web-Alt4"; 6666 -> "Web-Alt5"
        7000, 7001 -> "Web-Alt6"; 7070, 7077 -> "Web-Alt7"
        7443 -> "HTTPS-Alt2"; 7547 -> "CWMP"; 7675 -> "Web-Alt8"
        7777 -> "Web-Alt9"; 8000, 8008, 8009 -> "HTTP-Alt"
        8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090 -> "HTTP-Alt2"
        8180 -> "HTTP-Alt3"; 8222 -> "HTTP-Alt4"; 8243 -> "HTTPS-Alt3"
        8280 -> "HTTP-Alt5"; 8291 -> "Winbox"
        8443, 8444 -> "HTTPS-Alt4"; 8530, 8531 -> "HTTP-Alt6"
        8649 -> "Ganglia"; 8800 -> "Web-Alt10"; 8834 -> "Nessus"
        8880, 8888, 8889 -> "HTTP-Alt7"; 8983 -> "Solr"
        9000, 9001 -> "Web-Alt11"; 9043 -> "WebSphere"
        9060, 9080 -> "HTTP-Alt8"; 9090, 9091 -> "Web-Alt12"
        9100 -> "Printer"; 9200 -> "Elasticsearch"; 9290 -> "Web-Alt13"
        9300 -> "Elastic"; 9418 -> "Git"; 9443 -> "HTTPS-Alt5"
        9600 -> "Web-Alt14"; 9800 -> "Web-Alt15"; 9999 -> "Web-Alt16"
        10000, 10001 -> "Web-Alt17"; 10080 -> "HTTP-Alt9"
        11211 -> "Memcached"; 12345 -> "Web-Alt18"; 13337 -> "Web-Alt19"
        16010 -> "Web-Alt20"; 16379 -> "Web-Alt21"
        17000, 17001 -> "Web-Alt22"; 20000, 22000 -> "Web-Alt23"
        25565 -> "Minecraft"; 27017 -> "MongoDB"
        31337 -> "BackOrifice"; 32400 -> "Plex"
        32764 -> "Router"; 49152, 49154, 49155, 49156 -> "WindowsRPC"
        50000, 50100, 50200 -> "Web-Alt24"; 61616 -> "Web-Alt25"
        64738 -> "Mumble"; 65535 -> "Web-Alt26"
        else -> "Unknown"
    }

    private fun uiPost(msg: String, hex: String, ok: Boolean) {
        ui.post { status(msg, hex, ok) }
    }

    private fun uiPostResult(summary: String, text: String) {
        ui.post {
            tvResults.text = text
            tvSummary.text = summary
        }
    }

    private fun status(msg: String, hex: String, ok: Boolean) {
        statusText.text = msg
        statusText.setTextColor(Color.parseColor(hex))
        statusDot.setBackgroundColor(Color.parseColor(if (ok) "#2E7D32" else "#C62828"))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, "  $msg  ", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        bg.shutdown()
    }
}
