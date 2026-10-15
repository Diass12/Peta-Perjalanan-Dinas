# Dokumentasi Dinas (Android)

Aplikasi Android native (Kotlin + Jetpack Compose) untuk mencatat dokumentasi lapangan dan laporan kunjungan:
titik GPS + foto, temuan dengan tiga lensa (Aset / Teknis Operasional / Administrasi), ringkasan, tindak lanjut,
lampiran PDF, export Excel (foto tidak terpotong), mode presentasi, dan cadangan terenkripsi.

## Cara membangun APK (tanpa Android Studio)

1. Buat repositori GitHub **privat**, unggah seluruh isi folder ini.
2. Buka tab **Actions** → workflow **Build APK** → tunggu selesai.
3. Unduh artefak **apk** (`app-debug.apk`) dan pasang di HP (izinkan "pasang dari sumber ini").
4. Bila build gagal, unduh/salin log merah dari Actions dan kirim ke Claude untuk diperbaiki.

APK debug cukup untuk pemakaian pribadi. Untuk APK rilis bertanda tangan, buat keystore lalu isi secret repositori:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Membangun lokal
Butuh JDK 17 dan Android SDK (platform 36): `./gradlew assembleDebug`. Tes logika: `./gradlew testDebugUnitTest`.

## Struktur
- `domain/` model, parser koordinat, ringkasan (Kotlin murni)
- `security/` enkripsi berkas (AES-GCM berpotongan), Keystore, kunci aplikasi, penyimpanan media
- `data/` Room + SQLCipher, repository, cadangan (ZIP, opsional berkata sandi)
- `export/` penulis .xlsx sendiri (foto, gabung sel, tautan peta)
- `location/`, `media/` GPS, EXIF, pipeline foto, penampil terenkripsi
- `ui/` layar Compose, peta osmdroid (OSM + satelit Esri), tema Klasik/Langit

## Catatan versi
Versi pustaka di `gradle/libs.versions.toml` ditulis dari pengetahuan, belum diuji build. Bila Gradle menolak
satu versi, ganti ke versi stabil terdekat.
