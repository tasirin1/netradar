# Contributing — NetRadar

Terima kasih sudah mau berkontribusi! Panduan singkat ini menjaga repo tetap
rapi dan rilis selalu hijau. **Untuk AI yang mengelola repo: baca
[AGENTS.md](AGENTS.md) dulu — dokumen ini aturan ringkas untuk kontributor.**

## Alur kerja

1. **Satu commit/PR, satu tujuan.** Perubahan besar yang mencampur banyak
   hal sulit di-review dan sulit di-rollback.
2. **Wajib entri `CHANGELOG.md`** untuk perubahan perilaku/fitur/perbaikan
   (lihat aturan maraton di AGENTS.md).
3. **Build rilis hanya lewat GitHub Actions** — push ke branch mana pun
   menjalankan workflow Build; rilis diterbitkan dari tag `vX.Y.Z` sesudah
   `versionName` dinaikkan manual di `app/build.gradle.kts`.
4. **Jangan commit keystore** (`*.jks`, `keystore.b64` sudah di-`.gitignore`).

## Aturan bahasa & gaya

- Kode, komentar, string UI, dan commit memakai **Bahasa Indonesia**
  (beberapa label scan memakai istilah teknis Inggris — disengaja).
- Gaya commit: `type: deskripsi` — `feat`, `fix`, `refactor`, `test`,
  `perf`, `docs`, `chore`. Satu commit satu tujuan.
- Jaga kompatibilitas Android 10 (minSdk 29); logika murni wajib punya
  unit test JVM di `app/src/test/` (tanpa Robolectric).

## Dependabot

Dependabot **sengaja dimatikan** di repo ini (keputusan tercatat di
CHANGELOG): tidak ada PR update otomatis. Update dependensi dilakukan
manual dan bertahap lewat CI.

## Label otomatis & issue/PR lama

- Workflow `Labeler` memberi label otomatis per path (lihat
  `.github/labeler.yml`).
- Workflow `Stale` menandai dan menutup issue/PR yang lama tidak aktif.

## Guard lokal

```bash
python3 scripts/check_repo.py   # pemeriksaan konsistensi repo
bash scripts/cek-cepat.sh       # cek cepat pra-push (memanggil check_repo)
```

Pasang kait pre-push sekali per clone agar guard berjalan otomatis:

```bash
sh tools/pasang-hook.sh
```

Kerentanan keamanan tidak boleh dibuat sebagai issue publik; ikuti
[SECURITY.md](SECURITY.md).
