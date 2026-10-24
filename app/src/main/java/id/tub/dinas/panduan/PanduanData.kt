package id.tub.dinas.panduan

// BERKAS HASIL GENERATE dari panduan-data/panduan.json (jangan diedit manual; ubah JSON lalu jalankan gen.py).
// Data sampel: lihat [PanduanData.SUMBER]. Web/exe memakai data yang sama (panduan-data.js).

class Wisata(val id: String, val nama: String, val kategori: String, val deskripsi: String, val durasi: String, val tag: List<String>, val foto: String)
class Kuliner(val id: String, val nama: String, val kategori: String, val deskripsi: String, val halal: String)
class Kota(val id: String, val nama: String, val wilayah: String, val ikon: String, val tagline: String, val ringkas: String, val waktu: String, val durasi: String, val wisata: List<Wisata>, val kuliner: List<Kuliner>)

object PanduanData {
    const val VERSI = 1
    const val SUMBER = "Data sampel disusun manual untuk demo; belum diverifikasi sumber resmi. Jam buka, tiket, dan aturan kunjungan bisa berubah, cek langsung sebelum berangkat."
    val kota: List<Kota> = listOf(
        Kota("bali", "Bali", "Provinsi Bali", "🏝️", "Pura, pantai, dan sawah terasering", "Pulau dengan budaya Hindu yang hidup, pantai untuk berselancar dan menikmati senja, serta lanskap sawah dan pegunungan di Ubud.", "Sekitar April sampai Oktober (musim kemarau)", "4 sampai 5 hari",
            listOf(
                Wisata("tanah-lot", "Pura Tanah Lot", "Budaya", "Pura laut di atas batu karang di Tabanan, terkenal dengan pemandangan matahari terbenam.", "1 sampai 2 jam", listOf("budaya", "alam"), "Datang menjelang sore untuk senja; periksa kondisi pasang surut."),
                Wisata("uluwatu", "Pura Luhur Uluwatu", "Budaya", "Pura di tebing tinggi di ujung selatan Bali dengan pemandangan Samudra Hindia. Pertunjukan tari Kecak biasanya digelar sore hari.", "2 jam", listOf("budaya", "alam"), "Tepi tebing saat senja. Kera di area ini sering merebut barang, simpan kacamata dan ponsel."),
                Wisata("pantai-kuta", "Pantai Kuta", "Pantai", "Pantai berpasir luas dekat bandara, populer untuk belajar berselancar dan menikmati senja.", "2 jam", listOf("alam", "keluarga", "gratis"), ""),
                Wisata("tegallalang", "Sawah Terasering Tegallalang", "Alam", "Sawah berundak di utara Ubud dengan pemandangan lembah hijau.", "1 sampai 2 jam", listOf("alam"), "Pagi hari saat belum ramai dan cahaya lembut."),
                Wisata("monkey-forest", "Monkey Forest Ubud", "Alam", "Hutan kecil di Ubud berisi pura dan kera ekor panjang. Simpan barang kecil dan makanan dengan aman.", "1 sampai 2 jam", listOf("alam", "keluarga"), ""),
            ),
            listOf(
                Kuliner("ayam-betutu", "Ayam Betutu", "Makanan Khas", "Ayam atau bebek berbumbu rempah khas Bali, dibungkus dan dimasak lama hingga empuk.", "Umumnya tanpa babi; tanyakan penjual."),
                Kuliner("babi-guling", "Babi Guling", "Makanan Khas", "Babi panggang berbumbu rempah, disajikan dengan nasi dan lawar.", "Tidak halal (mengandung babi)."),
                Kuliner("sate-lilit", "Sate Lilit", "Jajanan", "Daging atau ikan cincang berbumbu yang dililitkan pada batang serai atau bambu lalu dibakar.", "Bervariasi; tanyakan isinya."),
                Kuliner("lawar", "Lawar", "Makanan Khas", "Campuran sayur, kelapa parut, dan daging cincang dengan bumbu khas.", "Sering mengandung babi; tanyakan."),
                Kuliner("nasi-campur-bali", "Nasi Campur Bali", "Makanan Khas", "Nasi dengan aneka lauk seperti ayam suwir, sate lilit, dan sambal matah.", "Bervariasi; tanyakan."),
            )),
        Kota("bandung", "Bandung", "Jawa Barat", "⛰️", "Kawah, kota kolonial, dan jajanan", "Kota sejuk di dataran tinggi dengan kawah belerang, bangunan kolonial, dan banyak jajanan.", "Sekitar Mei sampai September (lebih jarang hujan)", "2 sampai 3 hari",
            listOf(
                Wisata("kawah-putih", "Kawah Putih", "Alam", "Danau kawah belerang berair kehijauan di Ciwidey, Bandung Selatan, dengan udara sejuk.", "2 jam", listOf("alam"), "Pagi hari saat kabut tipis. Bau belerang menyengat, bawa masker bila perlu."),
                Wisata("tangkuban-parahu", "Gunung Tangkuban Parahu", "Alam", "Gunung berapi di Lembang dengan kawah yang bisa dikunjungi.", "2 jam", listOf("alam", "keluarga"), ""),
                Wisata("gedung-sate", "Gedung Sate", "Sejarah", "Bangunan ikonik berarsitektur kolonial, kini kantor Gubernur Jawa Barat dan memiliki museum.", "1 jam", listOf("sejarah", "gratis"), "Pagi hari agar cahaya bagus dan belum ramai."),
                Wisata("braga", "Jalan Braga", "Sejarah", "Jalan bersejarah berarsitektur kolonial, kini deretan kafe dan galeri.", "1 sampai 2 jam", listOf("sejarah", "malam", "gratis"), "Malam hari saat lampu jalan menyala."),
                Wisata("bosscha", "Observatorium Bosscha", "Edukasi", "Observatorium astronomi di Lembang yang membuka kunjungan untuk umum. Cek jadwal dan aturan sebelum datang.", "1 sampai 2 jam", listOf("keluarga", "alam"), ""),
            ),
            listOf(
                Kuliner("batagor", "Batagor", "Jajanan", "Bakso tahu goreng disajikan dengan saus kacang.", "Umumnya halal; pastikan di tempat."),
                Kuliner("siomay", "Siomay", "Jajanan", "Dimsum ikan ala Indonesia dengan kentang, kol, tahu, dan saus kacang.", "Umumnya halal; pastikan di tempat."),
                Kuliner("seblak", "Seblak", "Street Food", "Kerupuk basah dimasak pedas dengan bumbu kencur dan aneka isian.", "Umumnya halal; pastikan di tempat."),
                Kuliner("surabi", "Surabi", "Jajanan", "Serabi dari tepung beras dan santan dipanggang di tungku tanah liat, rasa manis atau gurih.", "Umumnya halal."),
                Kuliner("peuyeum", "Peuyeum", "Oleh-oleh", "Tape singkong fermentasi dengan rasa manis asam.", "Umumnya halal."),
            )),
        Kota("yogyakarta", "Yogyakarta", "DI Yogyakarta", "🏯", "Keraton, candi, dan kuliner manis", "Kota budaya dengan keraton yang masih berfungsi, candi abad ke-9, dan kuliner khas bercita rasa manis.", "Sekitar Mei sampai September", "3 hari",
            listOf(
                Wisata("keraton", "Keraton Yogyakarta", "Budaya", "Istana Kesultanan Yogyakarta yang masih berfungsi, dengan museum dan pertunjukan budaya.", "2 jam", listOf("budaya", "sejarah"), ""),
                Wisata("prambanan", "Candi Prambanan", "Sejarah", "Kompleks candi Hindu abad ke-9 dengan arsitektur menjulang.", "2 jam", listOf("sejarah", "budaya"), "Menjelang senja saat cahaya hangat."),
                Wisata("taman-sari", "Taman Sari", "Sejarah", "Bekas taman istana dengan kolam pemandian dan lorong-lorong lama.", "1 sampai 2 jam", listOf("sejarah", "budaya"), "Pagi hari agar lorong belum ramai."),
                Wisata("malioboro", "Jalan Malioboro", "Budaya", "Jalan utama pusat keramaian dengan pedagang dan toko oleh-oleh.", "2 jam", listOf("budaya", "malam", "gratis"), "Malam hari saat toko dan lampu jalan menyala."),
                Wisata("borobudur", "Candi Borobudur", "Sejarah", "Candi Buddha terbesar di Indonesia. Letaknya di Magelang, Jawa Tengah, sekitar satu sampai dua jam dari kota Yogyakarta.", "3 jam", listOf("sejarah", "budaya"), ""),
            ),
            listOf(
                Kuliner("gudeg", "Gudeg", "Makanan Khas", "Nangka muda dimasak lama dengan santan dan gula merah, disajikan dengan krecek, telur, dan ayam.", "Umumnya halal."),
                Kuliner("bakpia", "Bakpia", "Oleh-oleh", "Kue isi kacang hijau; kini tersedia banyak varian rasa.", "Umumnya halal; cek label."),
                Kuliner("sate-klathak", "Sate Klathak", "Makanan Khas", "Sate kambing berbumbu garam yang ditusuk dengan jeruji besi.", "Umumnya halal."),
                Kuliner("wedang-ronde", "Wedang Ronde", "Minuman", "Minuman jahe hangat dengan bola ketan berisi kacang.", "Umumnya halal."),
                Kuliner("tiwul", "Tiwul", "Jajanan", "Olahan singkong kering yang dikukus dan disajikan manis, khas Gunungkidul.", "Umumnya halal."),
            )),
        Kota("jakarta", "Jakarta", "DKI Jakarta", "🏙️", "Monumen, kota tua, dan kuliner Betawi", "Ibu kota dengan monumen nasional, kawasan kolonial, taman budaya, dan kuliner Betawi.", "Sepanjang tahun; musim kemarau sekitar Mei sampai September", "2 sampai 3 hari",
            listOf(
                Wisata("monas", "Monumen Nasional (Monas)", "Sejarah", "Tugu di Lapangan Medan Merdeka dengan museum sejarah di dasarnya dan pelataran pandang di puncak.", "2 jam", listOf("sejarah", "keluarga"), "Sore hari saat cahaya hangat."),
                Wisata("kota-tua", "Kota Tua Jakarta", "Sejarah", "Kawasan bangunan kolonial di sekitar Taman Fatahillah, dengan museum dan sewa sepeda.", "2 sampai 3 jam", listOf("sejarah", "gratis"), "Pagi hari agar lapangan belum penuh."),
                Wisata("istiqlal", "Masjid Istiqlal", "Budaya", "Masjid terbesar di Asia Tenggara, bersebelahan dengan Gereja Katedral.", "1 jam", listOf("budaya", "gratis"), ""),
                Wisata("tmii", "Taman Mini Indonesia Indah", "Keluarga", "Taman budaya dengan anjungan rumah adat dari berbagai provinsi.", "3 sampai 4 jam", listOf("keluarga", "budaya"), ""),
                Wisata("ancol", "Taman Impian Jaya Ancol", "Keluarga", "Kawasan rekreasi tepi laut di Jakarta Utara dengan taman hiburan dan akuarium.", "4 jam", listOf("keluarga"), ""),
            ),
            listOf(
                Kuliner("kerak-telor", "Kerak Telor", "Street Food", "Telur dan ketan sangrai dengan serundeng, dimasak di atas arang.", "Umumnya halal."),
                Kuliner("soto-betawi", "Soto Betawi", "Makanan Khas", "Soto berkuah santan atau susu dengan daging sapi atau jeroan.", "Umumnya halal."),
                Kuliner("gado-gado", "Gado-gado", "Makanan Khas", "Sayur rebus dengan lontong, tahu, tempe, dan saus kacang.", "Umumnya halal."),
                Kuliner("ketoprak", "Ketoprak", "Street Food", "Lontong, tahu, dan bihun dengan saus kacang dan kerupuk.", "Umumnya halal."),
                Kuliner("bir-pletok", "Bir Pletok", "Minuman", "Minuman rempah jahe khas Betawi, tanpa alkohol meski bernama bir.", "Umumnya halal."),
            )),
        Kota("lombok", "Lombok", "Nusa Tenggara Barat", "🌋", "Rinjani, pantai, dan gili", "Pulau dengan gunung berapi Rinjani, pantai pasir lembut, dan gugusan gili untuk snorkeling.", "Sekitar Mei sampai September (musim kemarau)", "4 hari",
            listOf(
                Wisata("rinjani", "Gunung Rinjani", "Alam", "Salah satu gunung berapi tertinggi di Indonesia dengan danau kawah Segara Anak. Pendakian butuh persiapan dan pemandu.", "2 sampai 4 hari (pendakian)", listOf("alam"), "Saat fajar dari jalur pendakian."),
                Wisata("senggigi", "Pantai Senggigi", "Pantai", "Pantai di pesisir barat dengan kafe dan penginapan, dikenal dengan senjanya.", "2 jam", listOf("alam", "keluarga"), "Senja dari pantai menghadap barat."),
                Wisata("gili-trawangan", "Gili Trawangan", "Pantai", "Pulau kecil di barat laut Lombok tanpa kendaraan bermotor, populer untuk snorkeling dan menyelam.", "1 sampai 2 hari", listOf("alam"), ""),
                Wisata("tanjung-aan", "Pantai Tanjung Aan", "Pantai", "Pantai berpasir lembut di Lombok Tengah dengan bukit di sekitarnya.", "2 jam", listOf("alam", "gratis"), "Dari bukit di sisi pantai saat pagi."),
                Wisata("sendang-gile", "Air Terjun Sendang Gile", "Alam", "Air terjun di kaki Gunung Rinjani, kawasan Senaru, Lombok Utara.", "2 jam", listOf("alam", "keluarga"), ""),
            ),
            listOf(
                Kuliner("ayam-taliwang", "Ayam Taliwang", "Makanan Khas", "Ayam bakar atau goreng dengan bumbu cabai yang pedas.", "Umumnya halal."),
                Kuliner("plecing-kangkung", "Plecing Kangkung", "Makanan Khas", "Kangkung rebus dengan sambal tomat terasi dan kacang tanah.", "Umumnya halal."),
                Kuliner("sate-rembiga", "Sate Rembiga", "Makanan Khas", "Sate daging sapi manis pedas dari daerah Rembiga, Mataram.", "Umumnya halal."),
                Kuliner("bebalung", "Bebalung", "Makanan Khas", "Sup tulang iga berbumbu rempah.", "Umumnya halal."),
                Kuliner("nasi-balap-puyung", "Nasi Balap Puyung", "Makanan Khas", "Nasi dengan ayam suwir pedas, kacang kedelai goreng, dan kerupuk khas Puyung.", "Umumnya halal."),
            )),
        Kota("labuan-bajo", "Labuan Bajo", "Nusa Tenggara Timur", "🐉", "Komodo dan pulau-pulau eksotis", "Pintu masuk ke Taman Nasional Komodo dengan pulau, pantai, dan spot snorkeling.", "Sekitar April sampai November", "3 sampai 4 hari",
            listOf(
                Wisata("pulau-komodo", "Pulau Komodo", "Alam", "Habitat asli komodo, hewan purba yang dilindungi. Kunjungan didampingi ranger.", "Setengah hari", listOf("alam", "keluarga"), ""),
                Wisata("pulau-padar", "Pulau Padar", "Alam", "Pulau dengan bukit pendakian pendek dan pemandangan teluk berpasir berbeda warna.", "2 jam", listOf("alam"), "Dari puncak bukit saat pagi atau sore."),
                Wisata("pantai-pink", "Pantai Pink", "Pantai", "Pantai berpasir kemerahan di kawasan Taman Nasional Komodo, populer untuk snorkeling.", "2 jam", listOf("alam"), "Dari perahu atau pantai saat matahari tinggi agar warna pasir terlihat."),
                Wisata("pulau-kanawa", "Pulau Kanawa", "Pantai", "Pulau kecil dengan pantai putih dan laut jernih untuk snorkeling.", "2 jam", listOf("alam"), ""),
                Wisata("batu-cermin", "Gua Batu Cermin", "Alam", "Gua dengan cahaya matahari yang masuk lewat celah batu, dekat kota Labuan Bajo.", "1 jam", listOf("alam", "keluarga"), "Siang hari saat berkas cahaya masuk."),
            ),
            listOf(
                Kuliner("ikan-bakar", "Ikan Bakar", "Seafood", "Ikan laut bakar dengan sambal dan nasi; banyak dijual di area wisata kuliner malam di tepi pantai.", "Umumnya halal."),
                Kuliner("kopi-flores", "Kopi Flores Bajawa", "Minuman", "Kopi arabika dari dataran tinggi Bajawa, Flores.", "Umumnya halal."),
                Kuliner("jagung-bose", "Jagung Bose", "Makanan Khas", "Bubur jagung pipil dengan kacang-kacangan, makanan tradisional Nusa Tenggara Timur.", "Umumnya halal."),
                Kuliner("sambal-luat", "Sambal Luat", "Makanan Khas", "Sambal pedas khas Flores yang dimakan bersama ikan bakar.", "Umumnya halal."),
                Kuliner("ikan-kuah-asam", "Ikan Kuah Asam", "Seafood", "Ikan laut dalam kuah asam segar yang bening.", "Umumnya halal."),
            )),
    )
}
