# Changelog

Semua perubahan penting proyek ini dicatat di file ini. Format mengikuti
[Keep a Changelog](https://keepachangelog.com/id/1.1.0/).

## [2.0] - 2026-08-21

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

### Diperbaiki (lint)
- Error lint `MissingPermission` pada notifikasi ditangani dengan `@SuppressLint` (guard `hasPermission()` sudah ada).
- API Compose yang deprecated diperbarui: `Divider` → `HorizontalDivider`,
  `LinearProgressIndicator(Float)` → overload lambda, ikon `Label`/`Sort` → `Icons.AutoMirrored.Filled.*`.
- Parameter composable yang tidak terpakai dibersihkan (`darkTheme`, `onTheme`, `scanSpeed`,
  `networkQualityColor`) beserta rantai pemanggilnya, dan variabel `deferred` yang menaungi nama dihilangkan.
