# Keamanan

| Aspek | Penerapan |
|---|---|
| Database | Room + SQLCipher (AES-256). Kunci acak 32 byte dibungkus kunci Android Keystore. |
| Foto & PDF | Dienkripsi AES-256-GCM per potongan 64 KB (integritas terjaga, potongan terpotong/tertukar ditolak). Disimpan di `filesDir`; database hanya menyimpan path relatif. |
| Kunci | Kunci DB dan media terpisah; kunci pembungkus tidak bisa diekspor dari Keystore. |
| Kunci aplikasi | BiometricPrompt (sidik jari/wajah) atau PIN/pola perangkat; kunci otomatis setelah waktu di latar belakang. |
| Layar | FLAG_SECURE saat kunci aktif (menyamarkan "aplikasi terbaru"); opsi blokir tangkapan layar selalu. |
| Cadangan | Backup Android dimatikan. Cadangan buatan sendiri (ZIP) dapat dienkripsi PBKDF2-HMAC-SHA256 (210.000 iterasi) + AES-GCM. Cadangan TANPA kata sandi berisi foto/PDF apa adanya. |
| Jaringan | Hanya untuk petak peta. Lalu lintas teks-jernih dilarang. Tidak ada analitik, iklan, atau pengiriman data. |
| Izin | Lokasi dan kamera saja (diminta saat dipakai), plus internet untuk peta. |
| Berkas masuk | PDF wajib berawalan `%PDF-`, tolak > 50 MB, peringatan > 12 MB; nama berkas dibersihkan; ZIP cadangan dilindungi dari zip-slip dan ukuran berlebih. |
| EXIF | Foto dikompres ulang; metadata lokasi asli tidak ikut tersimpan. |
| Rilis | R8 + penyusutan resource aktif; log debug dibuang. |

## Batas yang perlu diketahui
- Perangkat yang di-root dapat menembus proteksi Keystore.
- PDF didekripsi ke cache sementara saat dibuka lalu dihapus; bila aplikasi dimatikan paksa, berkas sisa dihapus saat aplikasi berikutnya dibuka.
- Excel hasil export dan cadangan tanpa kata sandi tidak terenkripsi; jaga ke mana berkas itu dikirim.
- Peta satelit memuat petak dari server Esri (alamat IP perangkat terlihat oleh penyedia peta).
