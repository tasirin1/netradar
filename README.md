# NetRadar

**Network Radar Scanner** — A powerful Android app for scanning and discovering devices on your local network.

## Features

| Feature | Description |
|---|---|
| **Port Scan** | Scan 25 common ports on target IP/CIDR |
| **CCTV** | Detect hidden IP cameras (RTSP, ONVIF, Hikvision, Dahua) |
| **Router** | Identify router brands and admin interfaces |
| **URL Path** | Find hidden web paths (admin, backup, config pages) |
| **Discover** | Comprehensive scan — cameras, routers, file shares, and services |

## How to Use

1. Enter target IP (e.g., `192.168.0.1`) or CIDR (e.g., `192.168.0.0/24`)
2. Tap a scan button
3. Results appear as clickable URLs — tap to open in browser
4. Tap **Stop** to cancel any running scan

## Download

Get the latest APK from [GitHub Actions](https://github.com/tasirin1/netradar/actions).

## Build

```bash
git clone https://github.com/tasirin1/netradar.git
cd netradar
./gradlew assembleRelease
```

## Author

**Julius Rudi Tasirin**

## License

MIT
