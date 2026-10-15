package id.tub.dinas.domain

data class SummaryStats(
    val total: Int,
    val allThree: Int,
    val done: Int,
    val inProgress: Int,
    val notStarted: Int,
    val perLens: Map<Lens, Int>,
)

object SummaryCalc {
    fun calc(list: List<TemuanModel>): SummaryStats {
        val done = list.count { it.status == Status.SELESAI }
        val pro = list.count { it.status == Status.PROSES }
        return SummaryStats(
            total = list.size,
            allThree = list.count { t -> Lens.values().all { t.isActive(it) } },
            done = done,
            inProgress = pro,
            notStarted = list.size - done - pro,
            perLens = Lens.values().associateWith { l -> list.count { it.isActive(l) } },
        )
    }

    /** Kalimat ringkas. Nol temuan tidak boleh error. */
    fun sentence(s: SummaryStats, titikCount: Int): String =
        if (s.total == 0)
            "$titikCount titik dokumentasi sudah dicatat. Belum ada temuan laporan."
        else
            "${s.allThree} dari ${s.total} temuan menyentuh ketiga lensa sekaligus. " +
                "Tindak lanjut: ${s.done} selesai, ${s.inProgress} dalam proses, ${s.notStarted} belum dimulai."

    /** Teks sel keterangan lensa untuk tabel (null = lensa tidak dipilih). */
    fun lensText(t: TemuanModel, l: Lens): String? {
        val v = t.lens[l] ?: return null
        return v.ifBlank { "(keterangan belum diisi)" }
    }
}
