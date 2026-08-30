# Panduan pengelolaan repo (untuk AI)

Baca file ini SEBELUM mengubah, memperbaiki, atau mengelola repository ini.
Panduan lengkap yang lain (fitur, cara pakai, troubleshooting) ada di
`README.md`.

## Struktur repository

```
.
├── .github/workflows/build.yml       # CI: build APK + unit test + release otomatis
├── app/build.gradle.kts              # minSdk 21 / targetSdk 35, compileSdk 35, R8
├── app/src/main/
│   ├── AndroidManifest.xml           # permission & komponen (activity, service, widget)
│   ├── res/                          # layout widget, tema, ikon, string
│   └── java/com/tasirin/network/radar/
│       ├── MainActivity.kt           # Activity utama (Compose) + izin notifikasi
│       ├── NetRadarApp.kt            # Application — crash handler + AppForeground
│       ├── ScanService.kt            # Foreground service penjaga proses scan
│       ├── model/
│       │   └── ScanModels.kt         # Semua data class, enum, PortRisk, ScanDiff
│       ├── scanner/
│       │   ├── ScannerManager.kt     # Manajemen job scan (start/pause/resume/stop)
│       │   ├── ScanLoop.kt           # Helper iterasi subnet paralel + retry + progress
│       │   ├── ScanCheckpoint.kt     # Posisi resume scan (subnet + host offset)
│       │   ├── ScanPause.kt          # Kontrol pause global antar scanner
│       │   ├── PortScanner.kt        # Port scan + deep scan 1–65535
│       │   ├── CameraScanner.kt      # Deteksi kamera CCTV (RTSP, ONVIF, Hikvision…)
│       │   ├── RouterScanner.kt      # Identifikasi router & halaman admin
│       │   ├── DiscoverScanner.kt    # Scan menyeluruh (kamera + router + share)
│       │   ├── UrlPathScanner.kt     # Temukan path web tersembunyi
│       │   ├── PingSweep.kt          # Ping sweep subnet
│       │   ├── UdpScanner.kt         # Probe UDP (DNS, NTP, SNMP, SSDP, mDNS)
│       │   ├── TracerouteScanner.kt  # Traceroute non-root via ping -t
│       │   └── MdnsNameResolver.kt   # Resolusi nama perangkat via mDNS/SSDP
│       ├── util/
│       │   ├── NetworkUtils.kt       # Ekspansi target, gateway, ARP, vendor MAC
│       │   ├── PingUtil.kt           # Ping + parse latency/TTL
│       │   ├── OsDetector.kt         # Tebakan OS dari TTL + port terbuka
│       │   ├── ResultsStore.kt       # Persist hasil scan ke SharedPreferences
│       │   ├── ScanCheckpointStore.kt# Persist posisi resume ke SharedPreferences
│       │   ├── ScanHistoryStore.kt   # Riwayat scan (file JSON, maks 100 entri)
│       │   ├── UptimeStore.kt        # Riwayat online/offline per IP
│       │   ├── PingStore.kt          # Riwayat latency ping per IP
│       │   ├── FavoritesStore.kt     # Daftar IP favorit (perangkat penting)
│       │   ├── SettingsStore.kt      # Preferensi aplikasi (tema, notifikasi, dll.)
│       │   ├── WakeOnLan.kt          # Kirim magic packet WoL (broadcast)
│       │   ├── SoundFeedback.kt      # Bunyi umpan balik saat host ditemukan
│       │   ├── CrashLog.kt           # Simpan jejak crash terakhir
│       │   └── AppForeground.kt      # Lacak apakah app di foreground
│       ├── viewmodel/
│       │   └── ScanViewModel.kt      # State UI + logika bisnis (scan, monitor, favorit)
│       ├── ui/
│       │   ├── components/           # Composable: HostCard, Dialogs, StatusBar, dll.
│       │   ├── screens/              # Halaman: MainScreen, ResultsTab, Pages
│       │   └── theme/                # Warna dan tema Compose
│       └── widget/
│           └── NetRadarWidget.kt     # Widget layar utama (daftar perangkat penting)
├── app/src/test/                     # Unit test JVM: NetworkUtils, OsDetector,
│                                     # ScanModels, PortRangeParser, MdnsNameResolver
└── gradle wrapper                    # build via ./gradlew (CI saja untuk rilis)
```

## Arsitektur ringkas

- **MainActivity** menampilkan UI Compose dengan 4 tab (Hasil, Monitor,
  Riwayat, Pengaturan); semua state dikelola **ScanViewModel** (StateFlow).
- **ScannerManager** menjalankan scan di coroutine IO; setiap scanner
  (Port/Camera/Router/Discover/Ping/UDP/Trace/UrlPath) menghasilkan
  `Flow<ScanEvent>` yang dikonsumsi ViewModel.
- **ScanLoop.scanSubnets()** adalah helper bersama: iterasi IP paralel per
  chunk, progress realtime, pause antar subnet, checkpoint resume, dan retry
  host timeout sekali.
- **Semaphore** (`speed.socketPermits`) membatasi koneksi socket paralel di
  semua scanner agar tidak kehabisan file descriptor saat scan /24.
- **Monitor** adalah loop ping berkala (3 detik semua host / 1,5 detik single);
  uptime + ping history disimpan batch (satu save per siklus).
- **Widget** membaca hasil dari SharedPreferences langsung (tanpa ViewModel).

## Keputusan & larangan historis

Hal berikut sengaja dipilih/dilarang — JANGAN diubah tanpa alasan kuat:

- **UI aplikasi Bahasa Indonesia** — sengaja; target pengguna lokal.
- **minSdk 21** — tetap Android 5+, jangan naikkan.
- **Compose BOM 2024.06.00** — dipilih karena fix bug "pending composition
  has not been applied" saat deep scan; jangan turunkan tanpa pengujian.
- **Animasi dot di StatusBar hanya saat scanning** — animasi infinite di dalam
  item LazyColumn memicu crash Compose saat daftar diukur ulang.
- **Progress UI wajib di-throttle** (maks ~6x/detik untuk list, ~4x/detik
  untuk deep scan) — update tiap frame menyebabkan jank/crash.
- **Jaringan jangan di main thread** — semua operasi socket/ping/DNS wajib
  di coroutine IO.
- **Socket wajib ditutup di finally** — pola try-finally untuk semua Socket,
  HttpURLConnection, dan Process agar tidak bocor resource.
- **WoL kirim ke broadcast address** (bukan unicast ke IP target) — perangkat
  mati tidak merespons ARP sehingga paket unicast tidak sampai.

## Pola bug yang pernah terjadi & guard-nya

| Pola bug | Penyebab | Guard |
|---|---|---|
| Socket leak di CameraScanner | `sock.close()` hanya di cabang tertentu | try-finally wajib untuk semua Socket |
| Race condition ScannerManager | `currentJob = null` sebelum launch baru | simpan ref lama, cancel, join |
| WoL tidak sampai | unicast ke IP target, bukan broadcast | kirim ke broadcast global + subnet |
| Zombie proses ping | `process.destroy()` tidak dipanggil | destroy() di blok finally |
| HTTP connection leak | `disconnect()` tidak tercapai saat exception | disconnect() di blok finally |
| ToneGenerator memory leak | dibuat tapi tidak pernah di-release | release saat error, buat ulang nanti |
| Progres retry tidak update | counter `completed` tidak di-increment | increment completed di callback retry |
| Notifikasi back online saling blokir | timestamp tunggal dibagi semua IP | map per-IP `lastBackOnlineAtByIp` |
| Hostname mDNS hilang saat rescan | hanya reverse DNS, tidak cek cache mDNS | fallback ke `MdnsNameResolver.nameFor()` |
| Riwayat uptime/ping orphan | clearResults hanya hapus host/URL | panggil `UptimeStore.clear()` + `PingStore.clear()` |
| Crash Compose "pending composition" | animasi infinite + update cepat LazyColumn | throttle UI + animasi hanya saat scanning |
| Deep scan force close | ribuan socket paralel habis FD | Semaphore DEEP_SCAN_CONCURRENCY=24 |

## Aturan pengembangan

1. **Build resmi HANYA via GitHub Actions** — jangan build lokal untuk rilis.
   Build lokal (`./gradlew assembleDebug`) hanya untuk debugging cepat.
2. **Bahasa**: kode, komentar, string UI, dan commit memakai **Bahasa Indonesia**
   (beberapa label scan memakai istilah teknis bahasa Inggris seperti
   "Port Scan"/"Discover" — itu disengaja).
3. **Gaya commit**: `type: deskripsi` — tipe yang dipakai di repo ini:
   `feat`, `fix`, `refactor`, `test`, `perf`, `docs`. Satu commit satu tujuan.
4. **Jangan ubah `versionName`** (`"2.0"` tetap). `versionCode` otomatis:
   `GITHUB_RUN_NUMBER` di CI, fallback jumlah commit git untuk build lokal.
5. **Jaga kompatibilitas Android 5 (minSdk 21)**: API baru wajib punya fallback;
   jangan naikkan minSdk.
6. **`targetSdk 35`**: pastikan perubahan tetap lolos perilaku storage/target
   SDK baru tanpa memutus Android 5–10.
7. **UI Compose**: hindari animasi berat per-item di `LazyColumn` selama deep
   scan; progress UI wajib di-throttle.
8. **Jangan taruh pekerjaan jaringan di main thread**; pemindaian wajib
   berjalan di coroutine/IO.
9. **Unit test**: logika murni (subnet, target expansion, ping, diff, OS guess)
   wajib punya test JVM di `app/src/test/` — jalankan lewat CI
   (`testDebugUnitTest`). Tanpa Context — dilarang Robolectric.
10. **Jangan commit keystore** — `.gitignore` sudah menutup `*.jks` &
    `keystore.b64`. CI menandatangani dengan keystore resmi Tasirin dari
    secrets repo.
11. **Socket & resource wajib ditutup**: gunakan try-finally untuk Socket,
    HttpURLConnection, Process, dan ToneGenerator. Jangan biarkan resource
    bocor saat exception.
12. **Semaphore wajib di semua scanner** yang membuka socket TCP/UDP —
    batasi dengan `speed.socketPermits` agar tidak kehabisan file descriptor.

## Menambah/mengubah fitur — file mana yang disentuh

- **Jenis scan baru** → `model/ScanModels.kt` (enum `ScanType`) + file baru di
  `scanner/` + daftarkan di `ScannerManager` + tombol di
  `ui/components/ScanControls.kt`.
- **Perilaku scan (loop, resume, jeda)** → `ScanLoop.kt`, `ScanCheckpoint.kt`,
  `ScanPause.kt`, `ScannerManager.kt`.
- **Target/network helper** → `util/NetworkUtils.kt` (+ test di
  `NetworkUtilsTest.kt`).
- **Resolusi nama perangkat (mDNS/SSDP)** → `scanner/MdnsNameResolver.kt`
  (cache dibangun sekali per scan di `ScannerManager`; fallback hostname di
  `ScanLoop`).
- **Risiko/deskripsi port** → `model/ScanModels.kt` (`PortRisk`/`PortRisks`),
  badge di `ui/components/ResultsList.kt` (`PortInfoDialog`).
- **UI hasil/filter/sort** → `ui/screens/ResultsTab.kt`, `Pages.kt`,
  `ui/components/ResultsList.kt`, `viewmodel/ScanViewModel.kt`.
- **Logika murni (diff scan, parser, tebakan OS)** → taruh fungsi di
  `model/`/`util/`/`scanner/` dan tulis unit test JVM di `app/src/test/`.
- **Pengaturan baru** → `util/SettingsStore.kt` (kunci baru) + UI di
  `MainScreen.kt`/halaman Pengaturan.
- **Widget** → `widget/NetRadarWidget.kt` + `res/layout/widget_netradar.xml`.
- **Notifikasi/service** → `ScanService.kt` + `ScanViewModel.kt`.
- **Versi app** → jangan manual; `versionCode` diatur CI.

## Cara memicu build

- **Push ke branch mana pun** → workflow `build.yml` jalan (trigger `on: push`).
- **Manual**: GitHub → Actions → *Build* → *Run workflow*.
- Hasil: unit test + APK release signed → artifact `NetScan-APK`;
  push ke `master` sekaligus publish GitHub Release `v2.0`.

## Verifikasi setelah build

```bash
gh run watch <run-id> --exit-status
gh run view <run-id> --json status,conclusion
gh run download <run-id> -n NetScan-APK
keytool -printcert -jarfile app-release.apk
```

Pastikan conclusion `success` dan artifact `NetScan-APK` ada. Uji manual:
pasang di HP, scan `192.168.0.0/24`, buka hasil & detail host, tes resume/jeda.

## Keputusan historis (dokumen hidup)

Pencatatannya penting supaya AI berikutnya tidak mengulang keputusan yang sudah
ditetapkan atau mengaktifkan kembali fitur/syarat yang sengaja dipertahankan.

| Keputusan | Alasan |
|---|---|
| UI/commit pakai Bahasa Indonesia | Basis pengguna utama lebih nyaman dengan istilah lokal; komentar dan commit juga konsisten. |
| Compose BOM 2024.06.00 | Compose Runtime 1.6.8 memperbaiki crash `pending composition has not been applied` saat deep scan memperbarui list beranimasi. |
| Throttle progress UI maks ~6×/detik | Menghindari kompetisi layout LazyColumn dengan recomposition berulang, terutama saat scan subnet besar. |
| minSdk tetap 21 & `versionName` tetap `"2.0"` | Dukungan Android 5.0+; `versionCode` diatur otomatis oleh CI (`GITHUB_RUN_NUMBER`). |
| Deep scan berbasis indeks/`IntArray` | Menghindari alokasi 65.536 objek port; menurunkan tekanan GC dan waktu scan. |
| ScanLoop mengekspansi subnet hanya saat benar-benar discan | Saat resume melewati subnet selesai, alokasi IP dihindari agar hemat memori. |
| Backup/restore menyertakan `ipConflict` & `lastSeenScan` | Menjaga konteks lintas scan agar UI tidak kehilangan status host. |

## Pola bug & guard

Catatkan setiap pola bug pernah terjadi supaya AI selalu menambahkan
pengaman ketika menyentuh area yang sama.

| Pola | Guard / aturan |
|---|---|
| Socket leak | Tutup socket, `Process`, `ToneGenerator`, dan `BufferedReader` di blok `finally`. |
| Kehabisan file descriptor saat scan /24 | Wajib ada `Semaphore` (`speed.socketPermits`) di semua scanner paralel. |
| Host timeout pada subnet ramai | Retry hanya sekali dan dibatasi `MAX_RETRY_HOSTS` agar scan luas tidak melambat. |
| Host membalas SYN-ACK semua port | Deep scan dihentikan pada `DEEP_SCAN_MAX_RESULTS` (4000 port) agar penyimpanan & UI tetap stabil. |
| Compose crash saat progress memperbarui `LazyColumn` | Gunakan throttle kemajuan dan hapus `StateFlow` dari thread komputasi saat recomposition aktif. |
| Backup kehilangan konteks lintas scan | `BackupManager` harus menuliskan `ipConflict`, `lastSeenScan`, dan `customPorts`. |
| Input port kustom tidak valid | Parser kembali ke port umum default; UI hanya menerima angka/range; dependensi `CustomPortParser` diuji terpisah. |
