# NetRadar

[![Build](https://github.com/tasirin1/netradar/actions/workflows/build.yml/badge.svg)](https://github.com/tasirin1/netradar/actions)
[![Release](https://img.shields.io/github/v/release/tasirin1/netradar)](https://github.com/tasirin1/netradar/releases)

**Network Radar Scanner** — pemindai jaringan lokal untuk Android: menemukan
perangkat, kamera CCTV tersembunyi, router, port terbuka, dan layanan di
jaringan Wi-Fi/ethernet. Dibangun dengan **Kotlin + Jetpack Compose**,
mendukung **Android 5.0 (API 21) ke atas**.

Repo ini juga **panduan pengelolaan untuk manusia maupun AI** (lihat
[Panduan pengelolaan repo](#panduan-pengelolaan-repo-untuk-manusia--ai)).

## Daftar isi

- [Fitur](#fitur)
- [Cara pakai](#cara-pakai)
- [Unduh](#unduh)
- [Widget, notifikasi & pengaturan](#widget-notifikasi--pengaturan)
- [Troubleshooting](#troubleshooting)
- [Build](#build)
- [Panduan pengelolaan repo (untuk manusia & AI)](#panduan-pengelolaan-repo-untuk-manusia--ai)
- [Lisensi](#lisensi)

## Fitur

| Fitur | Keterangan |
|---|---|
| **Port Scan** | 25 port umum pada target IP/CIDR, lengkap dengan banner & deteksi service |
| **CCTV** | Deteksi kamera IP tersembunyi (RTSP, ONVIF, Hikvision, Dahua) |
| **Router** | Identifikasi merek router & halaman admin |
| **URL Path** | Temukan path web tersembunyi (admin, backup, config) |
| **Discover** | Pemindaian menyeluruh — kamera, router, file share, dan service |
| **Ping Sweep / UDP / Traceroute** | Pemindaian tambahan sesuai kebutuhan |
| **Monitor** | Pantau perangkat secara berkala — uptime, online/offline, favorit |

Detail kemampuan:

- **Target fleksibel**: IP tunggal, CIDR (`192.168.0.0/24`), rentang IP penuh,
  lintas subnet otomatis, expand subnet satu ketukan.
- **Resume scan**: scan yang terhenti bisa dilanjutkan dari checkpoint
  (bukan mulai dari nol) + **ETA aktual** & retry host timeout.
- **Informasi host kaya**: hostname (reverse DNS + fallback mDNS/SSDP),
  MAC + vendor, latensi (sparkline), tebakan OS, IP conflict, penanda host
  lama (`lastSeen`), deteksi perangkat baru.
- **Risiko port**: badge tingkat bahaya (Kritis/Tinggi/Sedang/Rendah) di
  dialog detail port — bantu kenali layanan berbahaya (ADB, Telnet, Hikvision,
  Docker tanpa TLS).
- **Merge data hasil scan** dari beberapa sumber/putaran; ARP/DNS disimpan
  saat rescan.
- **Gateway dari tabel routing**, status internet WAN di header, indikator
  kualitas jaringan.
- **Favorit & monitor**: monitor bisa dibatasi hanya perangkat favorit;
  notifikasi perangkat penting offline / kembali online.
- **Foreground service** + notifikasi progress; scan tetap jalan saat layar
  terkunci; jeda otomatis saat app di background; batal deep scan.
- **Tombol Berhenti di notifikasi**, port kustom (`22, 80, 8000-8010`),
  port aktif pada deep scan, tema AMOLED, serta backup/impor JSON.
- **Widget layar utama** dengan pembaruan ter-throttle; **Wake-on-LAN**;
  **sonifikasi scan**; visualisasi "rasi bintang jaringan" + **share PNG**.
- **Riwayat scan**, log crash ber-timestamp, tema sistem/terang/gelap, mode
  ringkas, cegah layar mati, umpan balik suara.

## Cara pakai

1. Masukkan target — IP (mis. `192.168.0.1`) atau CIDR (mis. `192.168.0.0/24`).
2. Tekan tombol scan yang diinginkan (Port, CCTV, Router, URL Path, Discover,
   dll. — tombol ringkas di halaman utama / menu Lainnya).
3. Hasil tampil sebagai kartu host yang bisa diklik — tap untuk membuka
   URL/service di browser.
4. Tekan **Stop** untuk membatalkan; scan yang terhenti bisa **dilanjutkan**.

## Unduh

- **GitHub Release** → halaman [Release](https://github.com/tasirin1/netradar/releases)
  → APK `netradar-v2.0-<build>.apk` (rilis otomatis tiap push ke `master`).
- Alternatif: GitHub Actions → run terbaru → artifact `NetScan-APK`.
- APK di-build & ditandatangani otomatis oleh CI dengan **keystore resmi
  Tasirin** (sama dengan Tasirin Download Manager & Vaultwarden Host) —
  update-over-install mulus. Mendukung Android 5.0+.

## Widget, notifikasi & pengaturan

- **Widget**: tambahkan widget NetRadar di layar utama — ringkasan hasil scan
  terakhir dengan pembaruan berkala (throttle agar hemat baterai).
- **Notifikasi**: perangkat baru terdeteksi, perangkat penting offline/kembali
  online, dan scan selesai (bisa dimatikan di Pengaturan).
- **Pengaturan**: tema (sistem/terang/gelap), notifikasi, cegah layar mati,
  suara, dialog diff otomatis, mode ringkas, monitor hanya favorit.

## Troubleshooting

**Hasil scan kosong / tidak ada perangkat terdeteksi**
- Pastikan perangkat lain hidup & satu subnet; coba **Discover** (tidak
  bergantung ping gate), atau masukkan CIDR penuh `/24`.

**Scan lintas subnet tidak jalan**
- Sudah didukung otomatis — target IP/prefix dilanjutkan lintas subnet; untuk
  rentang besar app menampilkan konfirmasi sebelum scan luas.

**App crash saat deep scan (Compose)**
- Sudah ditangani (BOM Compose 1.6.8 + throttle progress di main thread).
  Kalau masih crash, kirim **log crash** (tab About/Crash) untuk laporan bug.

**Widget tidak terbarui**
- Widget memakai throttle; tunggu siklus pembaruan atau buka app agar hasil
  terbaru tersimpan.

## Build

### Resmi (GitHub Actions)

Versi toolchain (jangan diubah sembarangan — sudah disamakan dengan repo
Tasirin Download Manager): AGP `8.5.2`, Kotlin `1.9.24`, Compose Compiler
`1.5.8`, Gradle `8.9`. CI juga menjalankan `testDebugUnitTest` sebelum
`assembleRelease`.

Workflow `.github/workflows/build.yml` berjalan otomatis **setiap push** ke
branch mana pun: install Android SDK, jalankan unit test
(`testDebugUnitTest`), `assembleRelease` (signed), unggah APK sebagai artifact
`NetScan-APK`, lalu publish GitHub Release `v2.0` untuk push ke `master`.

APK release ditandatangani dengan **keystore resmi Tasirin** yang sama dengan
Tasirin Download Manager & Vaultwarden Host — tanda tangan konsisten sehingga
update di atas instalasi lama berjalan mulus tanpa uninstall.

Release juga di-**minify R8** (`isMinifyEnabled` + `shrinkResources`) sehingga
APK jauh lebih kecil: kode ikon material yang tak terpakai, resource locale
library, dan kode mati lain otomatis dibuang tiap build.

### Lokal (debug/testing)

```bash
./gradlew assembleDebug
# Hasil: app/build/outputs/apk/debug/app-debug.apk
```

Build lokal hanya untuk debugging cepat — bukan sumber rilis resmi.

**Persyaratan**: Android 5.0+ (minSdk 21), Java 17 + Android SDK.

---

# Panduan pengelolaan repo

Bagian ini untuk **manusia maupun AI** yang ingin memahami, mengubah, atau
mengelola repository ini dengan benar.

## Struktur repository

```
.
├── .github/workflows/build.yml       # CI: SDK → test → assembleRelease (keystore resmi) → release
├── app/build.gradle.kts              # minSdk 21 / targetSdk 35, Compose, signing via env
├── app/src/main/
│   ├── AndroidManifest.xml           # permission jaringan/notifikasi + service & widget
│   ├── java/com/tasirin/network/radar/
│   │   ├── NetRadarApp.kt            # Application
│   │   ├── MainActivity.kt           # Entry + host Compose UI
│   │   ├── ScanService.kt            # Foreground service (notifikasi progress scan)
│   │   ├── model/ScanModels.kt       # HostInfo, PortInfo, ScanType, SortMode, filter
│   │   ├── scanner/                  # MESIN SCAN — lihat Arsitektur
│   │   │   ├── ScannerManager.kt     # Orkestrator scan (pause/resume/stop)
│   │   │   ├── ScanLoop.kt           # Loop deep scan + progress
│   │   │   ├── ScanCheckpoint.kt / ScanPause.kt   # Resume & jeda
│   │   │   ├── DiscoverScanner.kt / PingSweep.kt / PortScanner.kt
│   │   │   ├── RouterScanner.kt / CameraScanner.kt / UrlPathScanner.kt
│   │   │   ├── UdpScanner.kt / TracerouteScanner.kt
│   │   ├── ui/screens/MainScreen.kt  # Layar utama (target, tombol scan, hasil)
│   │   ├── ui/components/            # ScanControls, StatusBar, ResultsList
│   │   ├── ui/theme/                 # Color.kt, Theme.kt (sistem/terang/gelap)
│   │   ├── util/                     # NetworkUtils, SettingsStore, PingUtil, OsDetector,
│   │   │                             # ResultsStore, ScanHistoryStore, FavoritesStore,
│   │   │                             # UptimeStore, PingStore, ScanCheckpointStore,
│   │   │                             # WakeOnLan, SoundFeedback, CrashLog, AppForeground
│   │   ├── viewmodel/ScanViewModel.kt# State scan + progress → UI
│   │   └── widget/NetRadarWidget.kt  # Widget layar utama (throttle)
│   ├── res/layout/widget_netradar.xml, res/xml/widget_info.xml
│   └── app/src/test/.../NetworkUtilsTest.kt   # Unit test logika subnet/ping
└── gradle wrapper                    # build via ./gradlew (CI untuk rilis)
```

## Arsitektur ringkas

- **ScanViewModel** memegang state (target, hasil, progress, filter/sort) dan
  meneruskannya ke Compose UI; memulai/berhenti scan lewat **ScannerManager**.
- **ScannerManager** memilih scanner sesuai `ScanType`, berjalan di coroutine,
  mendukung `pause()/resume()` (via `ScanPause`) dan `stop()` (cancel job).
- **ScanLoop + ScanCheckpoint** menjalankan deep scan bertahap: progress
  di-throttle ke UI (hindari crash Compose), checkpoint memungkinkan **resume**
  dari posisi terakhir, `ScanPause` menjeda saat app di background.
- **Scanner individual** (`DiscoverScanner`, `PingSweep`, `PortScanner`,
  `CameraScanner`, `RouterScanner`, `UrlPathScanner`, `UdpScanner`,
  `TracerouteScanner`) memakai `NetworkUtils` (expand target, baca ARP/DNS,
  gateway dari tabel routing, lookup vendor MAC, ping dengan `-W` di-clamp).
- **Hasil**: `ResultsStore` (hasil terakhir), `ScanHistoryStore` (riwayat),
  `FavoritesStore` (favorit), `UptimeStore`/`PingStore` (monitor) — semua
  SharedPreferences/JSON; `WakeOnLan` untuk bangun perangkat.
- **ScanService** (foreground) menjaga proses saat scan; **widget** membaca
  hasil tersimpan dengan pembaruan ter-throttle.

## Kunci SharedPreferences

`SettingsStore` memakai prefs `netradar_settings`:

| Kunci | Default | Fungsi |
|---|---|---|
| `theme` | `system` | `system` / `light` / `dark` |
| `notify_new` | `true` | Notifikasi perangkat baru |
| `notify_important` | `true` | Notifikasi perangkat penting offline |
| `notify_done` | `true` | Notifikasi scan selesai |
| `keep_screen_on` | `true` | Cegah layar mati saat scan |
| `sound_enabled` | `true` | Sonifikasi/umpan balik suara |
| `auto_diff_dialog` | `true` | Dialog diff hasil otomatis |
| `compact_mode` | `false` | Mode ringkas |
| `monitor_fav_only` | `false` | Monitor hanya perangkat favorit |

Store lain (`ResultsStore`, `ScanHistoryStore`, dll.) memakai prefs terpisah
sesuai namanya.

## Aturan pengembangan

1. **Build resmi HANYA via GitHub Actions** — jangan build lokal untuk rilis.
   Build lokal (`./gradlew assembleDebug`) hanya untuk debugging cepat.
2. **Bahasa**: kode, komentar, string UI, dan commit memakai **Bahasa Indonesia**
   (beberapa label scan memakai istilah teknis bahasa Inggris seperti
   "Port Scan"/"Discover" — itu disengaja).
3. **Gaya commit**: `type: deskripsi` — tipe yang dipakai di repo ini:
   `feat`, `fix`, `refactor`, `test`, `perf`, `docs` (contoh:
   `feat: resume scan, ETA aktual, ...`). Satu commit satu tujuan logis.
4. **Jangan mengubah `versionName`** (`"2.0"` tetap). `versionCode` otomatis:
   `GITHUB_RUN_NUMBER` di CI, fallback jumlah commit git untuk build lokal.
5. **Jaga kompatibilitas Android 5 (minSdk 21)**: API baru wajib punya fallback;
   jangan naikkan minSdk.
6. **`targetSdk 35`**: pastikan perubahan tetap lolos perilaku storage/target
   SDK baru tanpa memutus Android 5–10.
7. **UI Compose**: hindari animasi berat per-item di `LazyColumn` selama deep
   scan (riwayat crash "pending composition has not been applied" — BOM
   `2024.06.00` dipilih karena itu); progress UI wajib di-throttle.
8. **Jangan menaruh pekerjaan jaringan di main thread**; pemindaian wajib
   berjalan di coroutine/IO.
9. **Unit test**: logika murni (subnet, target expansion, ping) wajib punya
   test JVM di `app/src/test/` — jalankan lewat CI (`testDebugUnitTest`).
10. **Jangan commit keystore** — `.gitignore` sudah menutup `*.jks` &
    `keystore.b64`. CI menandatangani dengan keystore resmi Tasirin dari
    secrets repo (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
    `KEY_PASSWORD`); fallback `scanner.keystore` hanya untuk build lokal.

## Cara memicu build

- **Push ke branch mana pun** → workflow `build.yml` jalan (trigger `on: push`).
- **Manual**: GitHub → Actions → *Build* → *Run workflow*
  (atau `gh workflow run build.yml -R tasirin1/netradar`).
- Hasil: unit test + APK release signed (keystore resmi Tasirin) → artifact
  **`NetScan-APK`**; push ke `master` sekaligus publish GitHub Release
  **`v2.0`** (tag diupdate tiap build).

## Catatan penting & rekomendasi (keystore & release)

1. **NetRadar memakai keystore resmi Tasirin** — keystore yang sama dengan
   Tasirin Download Manager & Vaultwarden Host, disimpan sebagai secrets repo
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   Tanda tangan konsisten → update-over-install mulus dan Play Protect tidak
   menganggap app baru/asing.
2. **Release otomatis aktif**: setiap push ke `master`, workflow publish
   GitHub Release `v2.0` berisi APK `netradar-v2.0-<build>.apk`.
3. **Backup keystore selamanya** — hilang = tidak bisa update-over-install,
   dan kunci baru bikin Play Protect curiga.

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
  `model/`/`util/`/`scanner/` dan tulis unit test JVM di `app/src/test/`
  (tanpa Context — dilarang Robolectric).
- **Pengaturan baru** → `util/SettingsStore.kt` (kunci baru) + UI di
  `MainScreen.kt`/halaman Pengaturan.
- **Widget** → `widget/NetRadarWidget.kt` + `res/layout/widget_netradar.xml`.
- **Notifikasi/service** → `ScanService.kt` + `ScanViewModel.kt`.
- **Versi app** → jangan manual; `versionCode` diatur CI (lihat aturan di atas).

## Verifikasi setelah build

```bash
gh run watch <run-id> --exit-status
gh run view <run-id> --json status,conclusion
gh run download <run-id> -n NetScan-APK
keytool -printcert -jarfile app-release.apk   # pastikan SHA256 C2:78:5A:... (keystore Tasirin)
```

Pastikan conclusion `success` dan artifact `NetScan-APK` ada. Uji manual: pasang
di HP, scan `192.168.0.0/24`, buka hasil & detail host, tes resume/jeda.

## Lisensi

MIT — lihat [LICENSE](LICENSE).
