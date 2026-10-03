# Changelog

Semua perubahan penting proyek ini dicatat di file ini. Format mengikuti
[Keep a Changelog](https://keepachangelog.com/id/1.1.0/).

## [Unreleased]

### Diperbaiki
- Race `ScannerManager.stop()`: assignment `currentJob` kini di bawah kunci dan
  `finally` hanya meng-nol-kan job miliknya sendiri agar scan baru tak ter-cancel
  atau menjadi yatim.
- DNS di thread UI: ekspansi target saat `startScan`, resolve domain monitor
  tunggal, dan `refreshNetworkInfo` (gateway/`ip route`) kini berjalan di IO.
- Semaphore blokir (`java.util.concurrent`) di Port/Camera/Router/Discover
  diganti `kotlinx.coroutines.sync.Semaphore` agar antrean tidak menahan thread IO;
  `UdpScanner` yang sebelumnya tanpa batas kini memakai semaphore yang sama.
- Progress retry tidak lagi menghitung ganda (>100%), daftar retry dibatasi
  2000 IP agar tak OOM, dan offset checkpoint basi dijepit agar resume tak crash.
- Probe RTSP CCTV dibatasi 25 baris (seperti probe web) agar tak menggantung
  sampai timeout tiap port.
- Hasil scan kini otoritatif: port tertutup hilang dan host tak muncul bisa
  offline (sebelumnya union port menumpuk selamanya).
- Uptime/ping per host kini append di memori + simpan disk throttle 10 detik
  (dipaksa saat scan selesai/berhenti) agar tak O(n²) tiap host.
- Input port kustom selebar >1000 port ditolak (fallback port default) agar tak
  meledakkan coroutine/OOM — cakupan penuh tetap via deep scan.
- IPv6 ditolak eksplisit; satu IP penuh hanya memindai sisa /24-nya (bukan
  berekor ke /16 ±61rb host); ping menghormati timeout level via `waitFor`;
  traceroute mendukung IPv6 dan selesai tanpa syarat latency target.
- Cakupan uji ScanLoop: progres retry tak melebihi total, retry dilewati saat
  host hilang melebihi batas, dan checkpoint basi tidak crash.
- Progress pembuka scan ikut dijepit agar tak melebihi total saat resume basi.

### Diubah
- Workflow Build: push/PR yang hanya menyentuh docs (`**.md`, `LICENSE`,
  `.gitignore`) tidak lagi memicu build — hemat menit Actions dan nomor versi
  (selaras repo Tasirin lain).

## [2.0] - 2026-08-21

### Diubah (SDK target)
- `minSdk` dinaikkan dari 21 ke 29 (Android 10); `targetSdk`/`compileSdk` dari 35 ke 36.
- Guard API `Build.VERSION_CODES.O`/`N`/`M` yang kini selalu benar dihapus dari
  `ScanNotifier`, `ScanService`, dan `NetRadarWidget` (efficiency cleanup).
- `forceDarkAllowed` disatukan kembali ke `values/themes.xml` (resource
  `values-v29` tidak lagi dibutuhkan karena `minSdk 29`).

### Ditambahkan
- Tombol **Berhenti** pada notifikasi scan dan pemisahan logika notifikasi
  dari `ScanViewModel`.
- Export dan impor backup JSON untuk hasil scan, favorit, riwayat, serta pengaturan.
- Tema **AMOLED** dengan latar hitam murni.
- Input rentang port kustom untuk port scan dan deep scan.
- Tampilan port yang sedang dipindai pada progress deep scan.

### Diperbaiki
- Kebocoran socket, process, dan resource audio pada alur pemindaian.
- Race condition manajemen scanner dan state hasil.
- Progres retry, throttle notifikasi per-IP, hostname mDNS saat rescan,
  serta pembersihan status uptime/ping.
- Null-safety monitor tunggal dan penanganan error yang sebelumnya senyap.

### Keamanan
- CI memverifikasi fingerprint dan masa berlaku keystore rilis.
- Nilai rahasia tidak lagi menyertakan fallback langsung di workflow.

### Dioptimalkan
- Deep scan tidak lagi membuat daftar berisi 65.536 objek port untuk rentang penuh.
- Resume scan melewati subnet selesai tanpa memperluas IP-nya terlebih dahulu.
- Event retry dipadatkan, lookup vendor MAC dideduplikasi, dan dependensi preview dihapus.

### Ditambahkan
- Guard otomatis di CI: setiap perubahan kode/CI tanpa pembaruan `CHANGELOG.md` mengagalkan build.
- `lintDebug` dengan `abortOnError=true` dijalankan pada build resmi.
- `scripts/check_repo.py` sebagai guard lokal ringan (tanpa Android SDK) untuk struktur README, rahasia, dan aturan.
- Dokumen `AGENTS.md` kini memuat bagian **Keputusan historis** dan **Pola bug & guard**.
- Perubahan diharuskan lewat pull request agar status check CodeQL/build terkunci oleh protected branch.

### Diperbaiki (build & workflow)
- Workflow CI kini mengunggah `mapping.txt` (R8 ProGuard) sebagai aset release bersama APK untuk kemudahan deobfuscation crash.
- VirusTotal scan diberi `--connect-timeout` + `--retry` pada `curl` agar tidak gagal karena gangguan jaringan transient (SSL/connection reset).

### Diperbaiki (lint)
- Error lint `MissingPermission` pada notifikasi ditangani dengan `@SuppressLint` (guard `hasPermission()` sudah ada).
- API Compose yang deprecated diperbarui: `Divider` → `HorizontalDivider`,
  `LinearProgressIndicator(Float)` → overload lambda, ikon `Label`/`Sort` → `Icons.AutoMirrored.Filled.*`.
- Parameter composable yang tidak terpakai dibersihkan (`darkTheme`, `onTheme`, `scanSpeed`,
  `networkQualityColor`) beserta rantai pemanggilnya, dan variabel `deferred` yang menaungi nama dihilangkan.

### Diperbaiki (bug)
- **Socket leak di `RouterScanner`**: `sock.close()` hanya dipanggil di cabang sukses sehingga socket
  bocor saat `sock.connect()` gagal/timeout. Kini ditutup lewat `finally` (pola try-finally baku).
