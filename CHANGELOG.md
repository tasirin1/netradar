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
