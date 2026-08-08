# ProGuard/R8 — NetRadar

# Komponen yang didaftarkan di AndroidManifest (Activity, Service, Widget,
# Application) sudah otomatis di-keep oleh AGP; aturan di bawah hanya
# pengaman eksplisit agar tidak kehilangan entry point.
-keep class com.tasirin.network.radar.NetRadarApp { *; }
-keep class com.tasirin.network.radar.MainActivity { *; }
-keep class com.tasirin.network.radar.ScanService { *; }
-keep class com.tasirin.network.radar.widget.NetRadarWidget { *; }

# Ikon Compose (material-icons) dipakai lewat import wildcard — biarkan R8
# membuang ikon yang tidak direferensikan; tidak perlu keep rule khusus.
