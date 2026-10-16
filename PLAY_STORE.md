# Kesiapan Play Store — Dokumentasi Dinas

## Teknis (dicek otomatis di CI, job `play-check`)
- Build rilis dengan R8 (minify + shrink resources) + AAB (`bundleRelease`).
- Pustaka native selaras 16 KB (wajib untuk target SDK 35+ sejak Nov 2025).
- targetSdk 36, minSdk 26, applicationId `id.tub.dinas`, versionCode harus dinaikkan tiap unggahan (app/build.gradle.kts).
- Tanda tangan: tambahkan secret GitHub `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Daftarkan ke **Play App Signing**; simpan keystore upload di tempat aman (hilang = sulit pulih).
- Manifest: `allowBackup=false`, cleartext HTTP dilarang, FileProvider tidak diekspor.

## Izin & alasan (isi di Play Console → App content)
| Izin | Alasan |
|---|---|
| CAMERA | Foto titik dinas/aset, pindai barcode/QR & OCR kode aset |
| RECORD_AUDIO | Rekam video (opsional) untuk lampiran aset |
| ACCESS_FINE/COARSE_LOCATION | Menandai koordinat titik dinas & rute di peta |
| VIBRATE | Umpan balik getar saat pindai |
| INTERNET / ACCESS_NETWORK_STATE | Memuat ubin peta OpenStreetMap & rute (OSRM) |

Lokasi hanya di latar depan (tidak ada ACCESS_BACKGROUND_LOCATION).

## Data Safety (usulan jawaban)
- Data disimpan **di perangkat** (database terenkripsi SQLCipher, media terenkripsi AES-GCM). Tidak ada akun, iklan, atau analitik.
- Data dikirim keluar perangkat hanya: koordinat untuk permintaan rute ke server OSRM publik dan permintaan ubin peta OSM (IP/lokasi kasar tampil di server pihak ketiga). Isi jawaban "Lokasi — dibagikan — fungsi aplikasi" bila ingin konservatif.
- Pemrosesan OCR/barcode memakai ML Kit **bundled** (offline).
- Enkripsi saat transit: ya (HTTPS). Hapus data: pengguna dapat menghapus dinas/aset di aplikasi atau uninstall.

## Kebijakan privasi
Wajib berupa URL publik. Teks siap pakai ada di `PRIVACY.md`; repo ini privat, jadi salin ke halaman publik (GitHub Pages/Google Sites) lalu isi URL-nya di Play Console.

## Aturan akun baru
Akun pengembang pribadi baru: wajib uji tertutup ≥12 penguji selama 14 hari berturut-turut sebelum bisa rilis produksi. Akun organisasi (PT) dengan D-U-N-S tidak terkena aturan ini.

## Konten lain
Peringkat konten: isi kuesioner (hasil umumnya "Semua usia"). Kategori: Produktivitas/Bisnis. Target audiens: 18+.
