# NetRadar

[![Build](https://github.com/tasirin1/netradar/actions/workflows/build.yml/badge.svg)](https://github.com/tasirin1/netradar/actions)

**Network Radar Scanner** is a local-network scanner for Android built with
Kotlin and Jetpack Compose. It discovers hosts, cameras, routers, open ports,
and services on Wi-Fi or Ethernet networks. The app supports Android 5.0
(API 21) and newer.

## Features

- Port scanning with custom ranges such as `22, 80, 8000-8010`.
- Camera, router, URL path, ping sweep, UDP, traceroute, and discovery scans.
- Live monitoring, uptime history, latency charts, favorites, and Wake-on-LAN.
- Deep scan with visible current port, cancellation, and throttled updates.
- Scan checkpoints, wide-target confirmation, and resumable scans.
- AMOLED theme, compact mode, notification controls, and sound feedback.
- JSON backup/restore for results, favorites, history, and settings.

## Download

Get signed APK files from
[GitHub Releases](https://github.com/tasirin1/netradar/releases), or download
the `NetScan-APK` artifact from the latest successful GitHub Actions run.

## Build

The official release build runs in GitHub Actions:

```bash
./gradlew testDebugUnitTest assembleRelease
```

Release signing requires `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
and `KEY_PASSWORD`. Never commit keystore files or credentials.

## Documentation

The complete Indonesian documentation and repository rules are available in
[README.md](README.md) and [AGENTS.md](AGENTS.md).
