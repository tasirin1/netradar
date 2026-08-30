# NetRadar

[![Build](https://github.com/tasirin1/netradar/actions/workflows/build.yml/badge.svg)](https://github.com/tasirin1/netradar/actions)
[![Release](https://img.shields.io/github/v/release/tasirin1/netradar)](https://github.com/tasirin1/netradar/releases)

**Network Radar Scanner** is a local-network scanner for Android that finds devices, hidden CCTV cameras, routers, open ports, and services on Wi‑Fi or Ethernet networks. It is built with Kotlin and Jetpack Compose and supports Android 5.0 (API 21) and newer.

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

The feature set covers port, CCTV, router, URL path, discovery, ping sweep, UDP, traceroute, and periodic monitor scans with rich host information, favorites, resume support, custom ports, AMOLED theme, JSON backup/restore, and a notification stop button.

## Cara pakai

Enter a target IP or CIDR, choose the scan type, inspect host cards and service details, and stop or resume scans as needed.

## Unduh

Get signed APK files from [GitHub Releases](https://github.com/tasirin1/netradar/releases) or download the `NetScan-APK` artifact from the latest successful GitHub Actions run.

## Widget, notifikasi & pengaturan

The home screen widget, foreground notification, and settings page provide quick controls for monitoring, alerts, theme preference, and screen wake behavior.

## Troubleshooting

See the Indonesian README for detailed troubleshooting notes.

## Build

### Resmi (GitHub Actions)

Official builds are produced by GitHub Actions: unit tests, lint checks, signed release APK, keystore verification, VirusTotal scan, and size thresholds.

### Lokal (debug/testing)

```bash
./gradlew assembleDebug
# Hasil: app/build/outputs/apk/debug/app-debug.apk
```

Local builds are only for debugging and test runs. Never use them for releases.

---

# Panduan pengelolaan repo

See the governance document for full details.

## Struktur repository

High-level layout of source, workflows, tests, and documentation files.

## Arsitektur ringkas

Compose-based UI driven by `ScanViewModel` and scanner flows with a shared `ScanLoop` helper.

## Kunci SharedPreferences

Summary of the primary preference keys and persistence files.

## Aturan pengembangan

Development rules covering language, commit style, and testing obligations.

## Cara memicu build

Push, pull request, and manual workflow dispatch instructions.

## Catatan penting & rekomendasi (keystore & release)

Keystore security, release signing, and verification practices.

## Menambah/mengubah fitur — file mana yang disentuh

Guidance for which files to modify when adding or changing features.

## Verifikasi setelah build

CI and manual verification steps after a change.

## Lisensi

MIT License. Do not redistribute without permission.
