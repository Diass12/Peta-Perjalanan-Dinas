# Kebijakan Privasi — Dokumentasi Dinas

Terakhir diperbarui: Oktober 2026

Aplikasi "Dokumentasi Dinas" (id.tub.dinas) menyimpan semua data (titik dinas, foto, video, dokumen, data aset) **hanya di perangkat Anda**, dalam basis data terenkripsi dan berkas media terenkripsi. Data dinas, foto, dan dokumen tidak pernah dikirim ke server mana pun. Satu-satunya layanan milik pengembang adalah pemeriksa lisensi dan versi (Cloudflare Worker), yang hanya menerima kode lisensi dan ID perangkat acak untuk memeriksa keabsahan serta pencabutan lisensi, dan mengirim balik info versi aplikasi terbaru. Tidak ada akun pengguna dan tidak ada analitik.

**Izin yang digunakan**
- Kamera & mikrofon: mengambil foto/video dan memindai kode aset.
- Lokasi (saat aplikasi dibuka): mencatat koordinat titik dinas. Tidak ada pelacakan latar belakang.
- Pemeriksa lisensi dan versi: saat aplikasi dibuka online, kode lisensi Staff HO (bila ada) dan ID perangkat dikirim ke Cloudflare Worker milik pengembang; waktu pemakaian dicatat untuk pengelolaan lisensi.
- Internet: memuat ubin peta OpenStreetMap dan menghitung rute melalui layanan OSRM publik. Permintaan tersebut berisi koordinat/area peta yang dilihat dan alamat IP Anda, dan diterima penyedia layanan tersebut sesuai kebijakan mereka.

**Berbagi data**: tidak ada, kecuali ketika Anda sendiri mengekspor/membagikan berkas (Excel, PDF, HTML, cadangan) ke aplikasi lain.

**Penghapusan**: hapus data di dalam aplikasi atau copot pemasangan aplikasi.

**Kontak**: dias.arifmityanto@gmail.com
